package io.github.solcott.activitytracker.appview.sync

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.spaceRkeys
import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Keeps the activities in athletes' tier spaces indexed, as a member of each space (design decision 4):
 * - an athlete's client announces a space ([announce]); the AppView joins it with its own credential, registers for `notifyWrite` at
 *   [service], and backfills it;
 * - each notification ([notified]) pulls the space's ops since the last indexed revision;
 * - every space is also pulled every [reconcileInterval], since the authority's PDS sends each notification once, and its registration is
 *   renewed within [renewalMargin] of expiring.
 *
 * Only the authority writes to an athlete's space, so the authority's repo is the only one synced. Ops go through the [Indexer], which
 * keeps only activities and their streams. A space whose sync fails is retried with backoff; it never holds up other spaces or Jetstream.
 * Syncs of one space never overlap. The PDS may compact the op log, so a space that hasn't synced for [resyncAfter] is backfilled again.
 */
@Suppress("LongParameterList") // Collaborators, then tunables with defaults.
class SpaceSyncer(
    private val db: Database,
    private val indexer: Indexer,
    private val member: SpaceMember,
    /** The id this AppView registers for notifications: `<serviceDid>#activitytracker_space_syncer`. */
    private val service: String,
    private val clock: Clock = Clock.System,
    private val reconcileInterval: Duration = 15.minutes,
    private val renewalMargin: Duration = 2.hours,
    private val resyncAfter: Duration = 1.hours,
    parallelism: Int = 4,
) {
    private val log = LoggerFactory.getLogger(SpaceSyncer::class.java)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val permits = Semaphore(parallelism)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Spaces with a sync launched by [launchDue] and not yet finished, so a slow one is never launched twice. */
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Spaces notified while a sync of them ran, which may have read the log before the write: they're pulled again at once. */
    private val renotified = ConcurrentHashMap.newKeySet<String>()

    /** An athlete says their tier [space] exists. Idempotent; the space is synced soon after. */
    suspend fun announce(space: SpaceUri) {
        require(isSyncable(space)) { "Not an athlete tier space: $space" }
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            SpaceSync.insertIgnore {
                it[SpaceSync.space] = space.value
                it[authorityDid] = space.authority.value
                it[nextAttemptAt] = now
                it[createdAt] = now
            }
        }
        dueNow(space)
    }

    /** A verified `notifyWrite` for [space]: pull it soon. Returns false for a space this AppView doesn't sync. */
    suspend fun notified(space: SpaceUri): Boolean {
        val known = db.tx { SpaceSync.select(SpaceSync.space).where { SpaceSync.space eq space.value }.any() }
        if (known) dueNow(space)
        return known
    }

    /**
     * Runs until [scope] is cancelled: starts a sync of each due space, then waits for the next one to come due or a wakeup, without
     * waiting for the syncs it started. A failure to read what's due is logged and retried on the next round.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    launchDue(scope)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    log.warn("Reading the spaces due to sync failed; retrying: {}", e.message)
                }
                withTimeoutOrNull(POLL_INTERVAL) { wakeups.receive() }
            }
        }
    }

    /** Syncs every space that is due, a few at a time, and returns when they're done. Exposed for tests. */
    suspend fun runDue(scope: CoroutineScope) {
        launchDue(scope).forEach { it.join() }
    }

    /** Starts a sync of each due space that isn't syncing already, a few at a time; each wakes the loop when it ends. */
    private suspend fun launchDue(scope: CoroutineScope): List<Job> {
        val now = clock.now().toEpochMilliseconds()
        val due = db.tx {
            SpaceSync.select(SpaceSync.space).where { SpaceSync.nextAttemptAt lessEq now }.map { SpaceUri(it[SpaceSync.space]) }
        }
        return due.filter { running.add(it.value) }
            .map { space ->
                scope.launch {
                    try {
                        permits.withPermit { sync(space) }
                    } finally {
                        running.remove(space.value)
                        wakeups.trySend(Unit)
                    }
                }
            }
    }

    /** One sync of [space], isolated: a failure is logged and backed off, never thrown. */
    suspend fun sync(space: SpaceUri) {
        try {
            locks
                .computeIfAbsent(space.value) { Mutex() }
                .withLock {
                    val state = db.tx { SpaceSync.selectAll().where { SpaceSync.space eq space.value }.singleOrNull() } ?: return
                    renotified.remove(space.value)
                    try {
                        val now = clock.now()
                        val registeredUntil = state[SpaceSync.registeredUntil]
                        val renewed =
                            if (
                                registeredUntil == null || registeredUntil - renewalMargin.inWholeMilliseconds <= now.toEpochMilliseconds()
                            ) {
                                register(member, space, service)
                            } else {
                                registeredUntil
                            }
                        // Ops after the last revision may have been compacted away if the space hasn't synced for a while.
                        val rev =
                            state[SpaceSync.repoRev]?.takeIf { state.syncedWithin(resyncAfter, now) }?.let { pull(space, it) }
                                ?: backfill(space)
                        db.tx {
                            SpaceSync.update({ SpaceSync.space eq space.value }) {
                                it[repoRev] = rev
                                it[SpaceSync.registeredUntil] = renewed
                                it[failures] = 0
                                it[SpaceSync.syncedAt] = clock.now().toEpochMilliseconds()
                                val next = if (renotified.contains(space.value)) clock.now() else clock.now() + reconcileInterval
                                it[nextAttemptAt] = next.toEpochMilliseconds()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: XrpcException) {
                        if (e.error == SpaceErrors.SPACE_DELETED || e.error == SpaceErrors.SPACE_NOT_FOUND) dropped(space)
                        else failed(space, state, e)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        // Network, identity resolution or a malformed response: this space's problem only.
                        failed(space, state, e)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The database, reading the state or recording a failure: the space stays due and the next round retries it.
            log.warn("Syncing {} failed: {}", space, e.message)
        }
    }

    /**
     * First sync, or one after a gap: the head revision, then every activity and streams record as it stands, dropping indexed ones the
     * space no longer has, then ops after the head on later syncs. The PDS may have compacted the op log, so it can't stand in for the
     * records. Returns the head revision, or null if nothing was ever written.
     */
    private suspend fun backfill(space: SpaceUri): String? {
        val head = member.call(space, space.authority) { pds -> headRev(pds, space) }
        for (collection in listOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_STREAMS)) {
            val listed = mutableSetOf<String>()
            var cursor: String? = null
            do {
                val page =
                    member.call(space, space.authority) { pds ->
                        try {
                            pds.query(
                                Space.ListRecords.method,
                                Space.ListRecords.Params(space, space.authority, collection, limit = PAGE, cursor = cursor),
                            )
                        } catch (e: XrpcException) {
                            // The authority hasn't written to this space yet.
                            if (e.error != REPO_NOT_FOUND) throw e
                            Space.ListRecords.Output(emptyList())
                        }
                    }
                page.records.forEach { record ->
                    listed += record.rkey
                    record.value?.let { indexer.spaceWrite(space, record.collection, record.rkey, record.cid.value, it) }
                }
                cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
            } while (cursor != null)
            (db.tx { spaceRkeys(space, collection) } - listed).forEach { indexer.spaceDelete(space, collection, it) }
        }
        log.info("Backfilled {} at rev {}", space, head)
        return head
    }

    /** Applies the ops after [since], in order, and returns the revision now indexed. */
    private suspend fun pull(space: SpaceUri, since: String): String {
        var rev = since
        var cursor: String? = null
        do {
            val page =
                member.call(space, space.authority) { pds ->
                    pds.query(Space.ListRepoOps.method, Space.ListRepoOps.Params(space, space.authority, since, PAGE, cursor))
                }
            for (op in page.ops) {
                val value = op.value
                val cid = op.cid
                when {
                    cid == null -> indexer.spaceDelete(space, op.collection, op.rkey)
                    value != null -> indexer.spaceWrite(space, op.collection, op.rkey, cid.value, value)
                    else -> log.warn("{} op {}/{} at {} has no value; skipped", space, op.collection, op.rkey, op.rev)
                }
                rev = op.rev
            }
            page.commit?.let { rev = maxOf(rev, it.rev) }
            cursor = page.cursor
        } while (cursor != null)
        return rev
    }

    /** The space is gone: stop syncing it and drop what was indexed from it. */
    private suspend fun dropped(space: SpaceUri) {
        log.info("{} was deleted; dropping it", space)
        indexer.dropSpace(space)
        db.tx { SpaceSync.deleteWhere { SpaceSync.space eq space.value } }
        // Held by this sync, so no other waits on it; a later announcement makes a new one.
        locks.remove(space.value)
        renotified.remove(space.value)
    }

    private suspend fun failed(space: SpaceUri, state: ResultRow, e: Exception) {
        val failures = state[SpaceSync.failures] + 1
        val delay = minOf(BACKOFF_BASE * (1 shl minOf(failures - 1, BACKOFF_DOUBLINGS)), BACKOFF_MAX)
        log.warn("Syncing {} failed ({} in a row), retrying in {}: {}", space, failures, delay, e.message)
        db.tx {
            SpaceSync.update({ SpaceSync.space eq space.value }) {
                it[SpaceSync.failures] = failures
                it[nextAttemptAt] = (clock.now() + delay).toEpochMilliseconds()
            }
        }
    }

    private suspend fun dueNow(space: SpaceUri) {
        val now = clock.now().toEpochMilliseconds()
        renotified.add(space.value)
        db.tx { SpaceSync.update({ SpaceSync.space eq space.value }) { it[nextAttemptAt] = now } }
        wakeups.trySend(Unit)
    }

    companion object {
        /** The tiers that hold non-public activities. The public tier never does. */
        val TIERS = setOf(AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF)

        fun isSyncable(space: SpaceUri): Boolean = space.type == Nsid.SPACE_ATHLETE && space.skey in TIERS

        private const val PAGE = 100
        internal const val MAX_PAGE = 1000
        internal const val REPO_NOT_FOUND = "RepoNotFound"
        private val POLL_INTERVAL = 30.seconds
        private val BACKOFF_BASE = 1.minutes
        private val BACKOFF_MAX = 1.hours
        private const val BACKOFF_DOUBLINGS = 6
    }
}

/** The head revision of [space]'s authority repo, or null if it was never written. */
private suspend fun headRev(pds: XrpcClient, space: SpaceUri): String? {
    var cursor: String? = null
    while (true) {
        val page =
            try {
                pds.query(
                    Space.ListRepoOps.method,
                    Space.ListRepoOps.Params(space, space.authority, limit = SpaceSyncer.MAX_PAGE, cursor = cursor, excludeValues = true),
                )
            } catch (e: XrpcException) {
                if (e.error != SpaceSyncer.REPO_NOT_FOUND) throw e
                return null
            }
        if (page.commit != null || page.cursor == null) return page.commit?.rev ?: page.ops.lastOrNull()?.rev
        cursor = page.cursor
    }
}

/** Registers [service] for [space]'s `notifyWrite`s and returns when the registration expires, epoch millis. */
private suspend fun register(member: SpaceMember, space: SpaceUri, service: String): Long =
    member
        .call(space, space.authority) { pds -> pds.procedure(Space.RegisterNotify.method, Space.RegisterNotify.Input(space, service)) }
        .expiresAt
        .toEpochMilliseconds()

/** Whether this `space_sync` row last synced within [window] of [now]. */
private fun ResultRow.syncedWithin(window: Duration, now: Instant): Boolean =
    this[SpaceSync.syncedAt]?.let { now.toEpochMilliseconds() - it <= window.inWholeMilliseconds } ?: false
