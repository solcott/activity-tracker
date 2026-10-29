package io.github.solcott.activitytracker.appview.sync

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.spaceRkeys
import io.github.solcott.activitytracker.atproto.space.SpaceMember
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
 * Keeps the activities in athletes' tier spaces, and the likes in their interactions spaces, indexed, as a member of each space (design
 * decision 4):
 * - an athlete's client announces a space ([announce]); the AppView joins it with its own credential, registers for `notifyWrite` at
 *   [service], and backfills it;
 * - each notification ([notified]) pulls the space's ops since the last indexed revision, and applies them only once the commit ending the
 *   pull has a verified MAC ([SpaceMember.listRepoOps]) and its `data` matches the stored set hash ([LtHash]) with the ops folded in; a
 *   mismatch, which is how a `clearRecords` shows, backfills the space instead;
 * - every space is also pulled every [reconcileInterval], since the authority's PDS sends each notification once, and its registration is
 *   renewed within [renewalMargin] of expiring;
 * - a deleted space is dropped when the authority's PDS says so ([deleted]), or else when a sync is answered `SpaceDeleted`. A sync
 *   answered `SpaceNotFound`, which is also how the authority refuses a user (such as when it can't reach the managing app), keeps the
 *   space and backs off, and drops it only once every sync for [notFoundGrace] has been answered so.
 *
 * Only the authority writes to an athlete's space, so the authority's repo is the only one synced. Ops go through the [Indexer], which
 * keeps only what [Indexer.spaceCollections] allows for the space's key. A space whose sync fails is retried with backoff; it never holds
 * up other spaces or Jetstream. Syncs of one space never overlap. The PDS may compact the op log, so a space that hasn't synced for
 * [resyncAfter] is backfilled again.
 */
// Collaborators, then tunables with defaults; one space's lifecycle, from announcement to drop.
@Suppress("LongParameterList", "TooManyFunctions")
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
    private val notFoundGrace: Duration = 24.hours,
    parallelism: Int = 4,
) {
    private val log = LoggerFactory.getLogger(SpaceSyncer::class.java)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val permits = Semaphore(parallelism)
    /**
     * One lock per space, so syncs and drops of a space never overlap. Never removed: a waiter could still hold an old one when a new one
     * is made, and two syncs of the same space would run at once. A Mutex per space ever synced is small.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Spaces with a sync launched by [launchDue] and not yet finished, so a slow one is never launched twice. */
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Spaces notified while a sync of them ran, which may have read the log before the write: they're pulled again at once. */
    private val renotified = ConcurrentHashMap.newKeySet<String>()

    /** An athlete says their tier or interactions [space] exists. Idempotent; the space is synced soon after. */
    suspend fun announce(space: SpaceUri) {
        require(isSyncable(space)) { "Not a syncable athlete space: $space" }
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
        val known = isSynced(db, space)
        if (known) dueNow(space)
        return known
    }

    /**
     * A verified `notifySpaceDeleted` for [space]: drop it now, as a sync that finds it deleted does, rather than at its next sync. Waits
     * for a sync of it in progress, so nothing that sync indexes outlives the drop. Does nothing for a space this AppView doesn't sync.
     */
    suspend fun deleted(space: SpaceUri) {
        locks.computeIfAbsent(space.value) { Mutex() }.withLock { if (isSynced(db, space)) dropped(space, "was deleted") }
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
                        val synced = pullOrBackfill(space, state, now)
                        db.tx {
                            SpaceSync.update({ SpaceSync.space eq space.value }) {
                                it[repoRev] = synced.rev
                                it[setHash] = synced.state
                                it[notFoundSince] = null
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
                        when (e.error) {
                            SpaceErrors.SPACE_DELETED -> dropped(space, "was deleted")
                            SpaceErrors.SPACE_NOT_FOUND -> notFound(space, state, e)
                            else -> failed(space, state, e)
                        }
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
     * A pull after the last indexed revision, or a backfill: ops after it may have been compacted away if the space hasn't synced for a
     * while, a row with no set hash was synced before they were kept, and a pull that doesn't match its commit falls back to one.
     */
    private suspend fun pullOrBackfill(space: SpaceUri, state: ResultRow, now: Instant): Synced {
        val rev = state[SpaceSync.repoRev]
        val stored = state[SpaceSync.setHash]
        val pulled = if (rev != null && stored != null && state.syncedWithin(resyncAfter, now)) pull(space, rev, stored) else null
        return pulled ?: backfill(space)
    }

    /**
     * First sync, one after a gap, or one after a set-hash mismatch: the verified head commit, then every record the authority's repo holds
     * as it stands, indexing those the space's key allows, folded into a fresh set hash. Only if that matches the head's `data` are indexed
     * records the listing lacks dropped and the head recorded. A write landing mid-listing moves the head, and the backfill starts over, up
     * to [BACKFILL_ATTEMPTS] times; a mismatch at an unmoved head throws, keeping everything indexed. The PDS may have compacted the op
     * log, so it can't stand in for the records.
     */
    private suspend fun backfill(space: SpaceUri): Synced {
        var attempt = 0
        while (true) {
            attempt++
            val head = headCommit(member, space)
            val hash = LtHash()
            val indexed = Indexer.spaceCollections(space.skey)
            val listed = listAll(space, hash, indexed)
            if (hash.digest().contentEquals(head?.data?.decode() ?: EMPTY_DIGEST)) {
                for (collection in indexed) {
                    val kept = listed[collection].orEmpty()
                    (db.tx { spaceRkeys(space, collection) } - kept).forEach { indexer.spaceDelete(space, collection, it) }
                }
                log.info("Backfilled {} at rev {}", space, head?.rev)
                return Synced(head?.rev, hash.state())
            }
            val moved = headCommit(member, space)?.rev != head?.rev
            if (!moved || attempt >= BACKFILL_ATTEMPTS) {
                throw SetHashMismatchException(
                    "$space's records don't match its commit at ${head?.rev}${if (moved) ", still moving" else ""}"
                )
            }
            log.info("{} was written during its backfill; starting over", space)
        }
    }

    /**
     * Lists every record in [space]'s authority repo, folding each into [hash] and indexing those of the [indexed] collections, and returns
     * the rkeys listed per indexed collection.
     */
    private suspend fun listAll(space: SpaceUri, hash: LtHash, indexed: Set<String>): Map<String, Set<String>> {
        val listed = mutableMapOf<String, MutableSet<String>>()
        var cursor: String? = null
        do {
            val page =
                member.call(space, space.authority) { pds ->
                    try {
                        pds.query(Space.ListRecords.method, Space.ListRecords.Params(space, space.authority, limit = PAGE, cursor = cursor))
                    } catch (e: XrpcException) {
                        // The authority hasn't written to this space yet.
                        if (e.error != REPO_NOT_FOUND) throw e
                        Space.ListRecords.Output(emptyList())
                    }
                }
            page.records.forEach { record ->
                hash.add(LtHash.element(record.collection, record.rkey, record.cid.value))
                if (record.collection !in indexed) return@forEach
                listed.getOrPut(record.collection) { mutableSetOf() } += record.rkey
                record.value?.let { indexer.spaceWrite(space, record.collection, record.rkey, record.cid.value, it, whileSynced = true) }
            }
            cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
        } while (cursor != null)
        return listed
    }

    /**
     * Reads every page of ops after [since], folding each into the set hash [stored] (as of [since]), then, once the commit ending them has
     * a verified MAC and its `data` matches the folded hash, applies them in order and returns the commit's revision and hash. A failed MAC
     * throws before anything is applied; a pull that reaches no commit applies nothing and keeps [since]. A `data` mismatch (a cleared
     * repo, or anything else that left the hash behind) applies nothing and returns null, so the caller backfills.
     */
    private suspend fun pull(space: SpaceUri, since: String, stored: ByteArray): Synced? {
        val ops = mutableListOf<Space.ListRepoOps.Op>()
        val hash = LtHash(stored)
        var head: Space.ListRepoOps.Commit? = null
        var cursor: String? = null
        do {
            val page = member.listRepoOps(space, space.authority, Space.ListRepoOps.Params(space, space.authority, since, PAGE, cursor))
            for (op in page.ops) {
                op.prev?.let { hash.remove(LtHash.element(op.collection, op.rkey, it.value)) }
                op.cid?.let { hash.add(LtHash.element(op.collection, op.rkey, it.value)) }
            }
            ops += page.ops
            page.commit?.let { head = it }
            cursor = page.cursor
        } while (cursor != null)
        val commit = head
        return when {
            commit == null -> {
                if (ops.isNotEmpty()) log.warn("{} listed {} ops after {} but no commit; none applied", space, ops.size, since)
                Synced(since, stored)
            }
            !hash.digest().contentEquals(commit.data.decode()) -> {
                log.info("{} at {} doesn't match its set hash after {} ops since {}; backfilling", space, commit.rev, ops.size, since)
                null
            }
            else -> {
                apply(space, ops)
                Synced(commit.rev, hash.state())
            }
        }
    }

    /** Indexes pulled [ops] in order. */
    private suspend fun apply(space: SpaceUri, ops: List<Space.ListRepoOps.Op>) {
        for (op in ops) {
            val value = op.value
            val cid = op.cid
            when {
                cid == null -> indexer.spaceDelete(space, op.collection, op.rkey)
                value != null -> indexer.spaceWrite(space, op.collection, op.rkey, cid.value, value, whileSynced = true)
                else -> log.warn("{} op {}/{} at {} has no value; skipped", space, op.collection, op.rkey, op.rev)
            }
        }
    }

    /**
     * A sync answered `SpaceNotFound`: no such space, or the authority refusing the AppView, which looks the same. Keeps what was indexed
     * and backs off, unless every sync since [notFoundGrace] ago was answered so, which drops it.
     */
    private suspend fun notFound(space: SpaceUri, state: ResultRow, e: XrpcException) {
        val now = clock.now().toEpochMilliseconds()
        val since = state[SpaceSync.notFoundSince]
        if (since != null && now - since >= notFoundGrace.inWholeMilliseconds) {
            dropped(space, "has answered SpaceNotFound since ${Instant.fromEpochMilliseconds(since)}")
        } else {
            failed(space, state, e, notFoundSince = since ?: now)
        }
    }

    /** The space is gone, as [reason] says: stop syncing it and drop what was indexed from it. */
    private suspend fun dropped(space: SpaceUri, reason: String) {
        log.info("{} {}; dropping it", space, reason)
        indexer.dropSpace(space)
        db.tx { SpaceSync.deleteWhere { SpaceSync.space eq space.value } }
        renotified.remove(space.value)
    }

    /** A failed sync: back off. [notFoundSince] is the `SpaceNotFound` run this failure continues, or null for any other failure. */
    private suspend fun failed(space: SpaceUri, state: ResultRow, e: Exception, notFoundSince: Long? = null) {
        val failures = state[SpaceSync.failures] + 1
        val delay = minOf(BACKOFF_BASE * (1 shl minOf(failures - 1, BACKOFF_DOUBLINGS)), BACKOFF_MAX)
        log.warn("Syncing {} failed ({} in a row), retrying in {}: {}", space, failures, delay, e.message)
        db.tx {
            SpaceSync.update({ SpaceSync.space eq space.value }) {
                it[SpaceSync.failures] = failures
                it[SpaceSync.notFoundSince] = notFoundSince
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

        /** A tier holding non-public activities, or the interactions space holding likes on them. */
        fun isSyncable(space: SpaceUri): Boolean =
            space.type == Nsid.SPACE_ATHLETE && (space.skey in TIERS || space.skey == AthleteSpace.INTERACTIONS)

        private const val PAGE = 100
        internal const val MAX_PAGE = 1000
        internal const val REPO_NOT_FOUND = "RepoNotFound"
        private val POLL_INTERVAL = 30.seconds
        private val BACKOFF_BASE = 1.minutes
        private val BACKOFF_MAX = 1.hours
        private const val BACKOFF_DOUBLINGS = 6
        private const val BACKFILL_ATTEMPTS = 3

        /** The `data` of a repo with no records: sha256 of the zero LtHash state. */
        private val EMPTY_DIGEST = LtHash().digest()
    }
}

/** What a sync recorded: the authority repo's revision (null before its first write) and its LtHash state there. */
private class Synced(val rev: String?, val state: ByteArray)

/** A backfill's records never folded to its head commit's `data`. */
class SetHashMismatchException(message: String) : Exception(message)

/** The head commit of [space]'s authority repo, with a verified MAC, or null if the repo was never written. */
private suspend fun headCommit(member: SpaceMember, space: SpaceUri): Space.ListRepoOps.Commit? {
    var cursor: String? = null
    while (true) {
        val page =
            try {
                member.listRepoOps(
                    space,
                    space.authority,
                    Space.ListRepoOps.Params(space, space.authority, limit = SpaceSyncer.MAX_PAGE, cursor = cursor, excludeValues = true),
                )
            } catch (e: XrpcException) {
                if (e.error != SpaceSyncer.REPO_NOT_FOUND) throw e
                return null
            }
        if (page.commit != null || page.cursor == null) return page.commit
        cursor = page.cursor
    }
}

/** Whether this AppView syncs [space]. */
private suspend fun isSynced(db: Database, space: SpaceUri): Boolean = db.tx {
    SpaceSync.select(SpaceSync.space).where { SpaceSync.space eq space.value }.any()
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
