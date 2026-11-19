package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.BskyBlockBackfillFailures
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.isHandleSyntax
import io.github.solcott.activitytracker.appview.index.repoHoldingCollections
import io.github.solcott.activitytracker.appview.index.repoHoldingCount
import io.github.solcott.activitytracker.appview.index.spaceRkeyCount
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/** What an admin's lookup of an account found (admin/account-tools). */
sealed interface AccountLookup {
    /** A DID (indexed or not), or the account whose verified stored handle matched. */
    data class Found(val did: Did) : AccountLookup

    /** A handle no indexed account has as its verified handle. */
    data object NoMatch : AccountLookup

    /** Neither a DID nor a handle: nothing was looked up. */
    data object Malformed : AccountLookup

    companion object {
        /** [input] as a DID, or else as a handle (case and a leading `@` ignored) matched against verified stored handles only. */
        suspend fun of(db: Database, input: String): AccountLookup {
            val trimmed = input.trim()
            val handle = trimmed.removePrefix("@").lowercase()
            return when {
                isResolvableDid(trimmed) -> Found(Did(trimmed))
                !isHandleSyntax(handle) -> Malformed
                handle == IdentityResolver.INVALID_HANDLE.value -> NoMatch
                else -> byHandle(db, handle)?.let(::Found) ?: NoMatch
            }
        }

        private suspend fun byHandle(db: Database, handle: String): Did? = db.tx {
            Actors.select(Actors.did).where { Actors.handle eq handle }.orderBy(Actors.did).firstOrNull()?.get(Actors.did)?.let(::Did)
        }
    }
}

/**
 * What the AppView holds and knows about one account, as the account page shows it: identifiers, statuses, counts, times, revisions and
 * [ErrorSummary][io.github.solcott.activitytracker.appview.ops.ErrorSummary] text only. No field can carry record content: opt-ins are
 * booleans, so the grouping mode and leaderboard choices never reach the page, and space rows never hold the notify token or set hash.
 * Times are epoch ms.
 */
data class AccountSummary(
    val did: Did,
    /** Whether the account has an `actors` row; everything below is empty without one, except what is held under its DID anyway. */
    val indexed: Boolean,
    /** `active`, or the stored `takendown`, `suspended` or `deactivated`; null when not indexed. */
    val status: String?,
    val handle: String?,
    val handleVerified: Boolean,
    val handleCheckDueAt: Long?,
    val blocks: BlockBackfillState,
    val segmentMatching: Boolean,
    val grouping: Boolean,
    /** Per collection, the records held by origin (`repo`, or a space's key), zero counts and empty collections left out. */
    val counts: List<CollectionCounts>,
    val spaces: List<SpaceSyncState>,
) {
    /** Every origin [counts] names, the repo first, in a stable order. */
    val origins: List<String>
        get() = counts.flatMap { it.byOrigin.keys }.distinct().sortedWith(compareBy({ it != REPO }, { ORIGIN_ORDER.indexOf(it) }, { it }))

    companion object {
        const val REPO = "repo"
        private val ORIGIN_ORDER = listOf(REPO, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF, AthleteSpace.INTERACTIONS)

        suspend fun load(db: Database, did: Did): AccountSummary = db.tx {
            val actor =
                Actors.select(Actors.handle, Actors.status, Actors.handleCheckDueAt, Actors.bskyBlocksSyncedAt)
                    .where { Actors.did eq did.value }
                    .singleOrNull()
            val failure =
                BskyBlockBackfillFailures.select(
                        BskyBlockBackfillFailures.failures,
                        BskyBlockBackfillFailures.lastError,
                        BskyBlockBackfillFailures.nextAttemptAt,
                    )
                    .where { BskyBlockBackfillFailures.did eq did.value }
                    .singleOrNull()
            val handle = actor?.get(Actors.handle)
            AccountSummary(
                did = did,
                indexed = actor != null,
                status = actor?.let { it[Actors.status] ?: "active" },
                handle = handle,
                handleVerified = handle != null && handle != IdentityResolver.INVALID_HANDLE.value,
                handleCheckDueAt = actor?.get(Actors.handleCheckDueAt),
                blocks =
                    BlockBackfillState(
                        syncedAt = actor?.get(Actors.bskyBlocksSyncedAt),
                        failures = failure?.get(BskyBlockBackfillFailures.failures) ?: 0,
                        lastError = failure?.get(BskyBlockBackfillFailures.lastError),
                        nextAttemptAt = failure?.get(BskyBlockBackfillFailures.nextAttemptAt),
                    ),
                segmentMatching =
                    SegmentPrefs.select(SegmentPrefs.matching)
                        .where { SegmentPrefs.did eq did.value }
                        .singleOrNull()
                        ?.get(SegmentPrefs.matching) ?: false,
                grouping =
                    GroupMode.isOn(
                        GroupPrefs.select(GroupPrefs.mode).where { GroupPrefs.did eq did.value }.singleOrNull()?.get(GroupPrefs.mode),
                    ),
                counts = counts(did),
                spaces = spaceStates(did),
            )
        }

        /** The account's spaces: those it has `space_sync` rows for, plus its standard athlete spaces, whose records may outlive a row. */
        private fun JdbcTransaction.spacesOf(did: Did): List<SpaceUri> {
            val synced =
                SpaceSync.select(SpaceSync.space).where { SpaceSync.authorityDid eq did.value }.map { SpaceUri(it[SpaceSync.space]) }
            val standard = (SpaceSyncer.TIERS + AthleteSpace.INTERACTIONS).map { SpaceUri.of(did, Nsid.SPACE_ATHLETE, it) }
            return (standard + synced).distinctBy { it.value }
        }

        private fun JdbcTransaction.counts(did: Did): List<CollectionCounts> {
            val byCollection = sortedMapOf<String, MutableMap<String, Int>>()
            fun add(collection: String, origin: String, n: Int) {
                if (n > 0) byCollection.getOrPut(collection, ::mutableMapOf).merge(origin, n, Int::plus)
            }
            for (collection in repoHoldingCollections) add(collection, REPO, repoHoldingCount(did, collection))
            for (space in spacesOf(did)) {
                val origin = if (space.type == Nsid.SPACE_ATHLETE) space.skey else space.value
                for (collection in Indexer.spaceCollections(space.skey)) add(collection, origin, spaceRkeyCount(space, collection))
            }
            return byCollection.map { (collection, origins) -> CollectionCounts(collection, origins.toMap()) }
        }

        private fun JdbcTransaction.spaceStates(did: Did): List<SpaceSyncState> =
            // Only the columns shown: never `notify_token` or `set_hash`.
            SpaceSync.select(
                    SpaceSync.space,
                    SpaceSync.repoRev,
                    SpaceSync.syncedAt,
                    SpaceSync.failures,
                    SpaceSync.lastError,
                    SpaceSync.lastErrorAt,
                    SpaceSync.nextAttemptAt,
                    SpaceSync.notFoundSince,
                    SpaceSync.registeredUntil,
                )
                .where { SpaceSync.authorityDid eq did.value }
                .orderBy(SpaceSync.space, SortOrder.ASC)
                .map {
                    SpaceSyncState(
                        space = SpaceUri(it[SpaceSync.space]),
                        repoRev = it[SpaceSync.repoRev],
                        syncedAt = it[SpaceSync.syncedAt],
                        failures = it[SpaceSync.failures],
                        lastError = it[SpaceSync.lastError],
                        lastErrorAt = it[SpaceSync.lastErrorAt],
                        nextAttemptAt = it[SpaceSync.nextAttemptAt],
                        notFoundSince = it[SpaceSync.notFoundSince],
                        registeredUntil = it[SpaceSync.registeredUntil],
                    )
                }
    }
}

/** When the account's Bluesky blocks were last backfilled (null: never), and while failing, how often, why and when next. */
data class BlockBackfillState(val syncedAt: Long?, val failures: Int, val lastError: String?, val nextAttemptAt: Long?)

/** How many [collection] records are held from each origin. */
data class CollectionCounts(val collection: String, val byOrigin: Map<String, Int>)

/** One `space_sync` row, without its secrets. */
data class SpaceSyncState(
    val space: SpaceUri,
    val repoRev: String?,
    val syncedAt: Long?,
    val failures: Int,
    val lastError: String?,
    val lastErrorAt: Long?,
    val nextAttemptAt: Long,
    val notFoundSince: Long?,
    val registeredUntil: Long?,
)
