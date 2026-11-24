package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.AppViewConfig.ReindexLimits
import io.github.solcott.activitytracker.appview.auth.Multikey
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.RepoReindexes
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.ReindexWatch
import io.github.solcott.activitytracker.appview.index.RepoWriteResult
import io.github.solcott.activitytracker.appview.ipld.CarFile
import io.github.solcott.activitytracker.appview.ipld.CarLimits
import io.github.solcott.activitytracker.appview.ipld.RepoFormatException
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.appview.ops.attempt
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Rebuilds one account's indexed state from its repo, on an operator's request (admin/repo-reindex). Internal only: the admin site's
 * operator actions call [request] and read [runs]; nothing public reaches it.
 *
 * A run resolves the account's DID document afresh, starts watching its Jetstream commits ([ReindexWatch]), fetches its whole repo with
 * `com.atproto.sync.getRepo` from the PDS that document names (over [http], the guarded client), checks the commit's signature against the
 * document's key and every block it reads against its CID, then indexes every record of an indexed collection whose CID differs from the
 * indexed one (or that isn't held, where the index keeps no CID), and deletes what the AppView holds from the repo that the repo no longer
 * has. Finally it forces a resync of the account's spaces ([resyncSpaces]). Progress and outcome are stored in [RepoReindexes]; at most one
 * run per account, and [ReindexLimits.parallelism] in all, run at once.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Collaborators, each swappable in tests; one run's steps.
class RepoReindexer(
    private val db: Database,
    private val indexer: Indexer,
    private val http: HttpClient,
    /** The account's DID document, resolved afresh (through the guarded identity resolver). */
    private val resolveDid: suspend (Did) -> DidDocument,
    /** Forces a resync of the account's spaces, returning how many were queued; null when the AppView syncs no spaces. */
    private val resyncSpaces: (suspend (Did) -> Int)?,
    private val limits: ReindexLimits = ReindexLimits(),
    private val clock: Clock = Clock.System,
    /** Where fetched repos are spooled; the system's temp folder when null. */
    private val tempDir: Path? = null,
    /** Runs the blocking reads of the spooled repo. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(RepoReindexer::class.java)
    private val requests = Mutex()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** Runs started by this process and not finished, by id. */
    private val running = ConcurrentHashMap<Long, Did>()

    /**
     * Queues a reindex of [did] for [requestedBy], an operator, and returns it; a queued or running reindex of [did] is returned instead
     * (one per account). [resyncSpaces] also forces a resync of the account's spaces at the end.
     */
    suspend fun request(did: Did, requestedBy: Did, resyncSpaces: Boolean = true): ReindexRun = requests.withLock {
        val run = db.tx {
            val active =
                RepoReindexes.selectAll()
                    .where { (RepoReindexes.did eq did.value) and (RepoReindexes.state inList ACTIVE) }
                    .orderBy(RepoReindexes.id)
                    .firstOrNull()
            active?.let(ReindexRun::of)
                ?: RepoReindexes.insert {
                        it[RepoReindexes.did] = did.value
                        it[RepoReindexes.requestedBy] = requestedBy.value
                        it[requestedAt] = clock.now().toEpochMilliseconds()
                        it[RepoReindexes.resyncSpaces] = resyncSpaces
                        it[state] = ReindexState.QUEUED.code
                        it[recordsSeen] = 0
                        it[recordsUnchanged] = 0
                        it[recordsIndexed] = 0
                        it[recordsInvalid] = 0
                        it[recordsRemoved] = 0
                        it[recordsLive] = 0
                    }
                    .let { inserted -> runRow(inserted[RepoReindexes.id]) }
        }
        wakeups.trySend(Unit)
        run
    }

    /** The run with [id], if it's still kept. */
    suspend fun run(id: Long): ReindexRun? = db.tx {
        RepoReindexes.selectAll().where { RepoReindexes.id eq id }.singleOrNull()?.let(ReindexRun::of)
    }

    private fun JdbcTransaction.runRow(id: Long): ReindexRun =
        RepoReindexes.selectAll().where { RepoReindexes.id eq id }.single().let(ReindexRun::of)

    /** [did]'s runs, newest first. */
    suspend fun runs(did: Did, limit: Int = DEFAULT_RUNS): List<ReindexRun> = db.tx {
        RepoReindexes.selectAll()
            .where { RepoReindexes.did eq did.value }
            .orderBy(RepoReindexes.id, SortOrder.DESC)
            .limit(limit)
            .map(ReindexRun::of)
    }

    /**
     * Across every account, the runs queued or running now and those that failed at or after [since] (epoch ms), newest first, for the
     * admin site (admin/account-tools). A run that ended `unavailable` isn't a failure.
     */
    suspend fun recentRuns(since: Long, limit: Int = RECENT_RUNS): List<ReindexRun> = db.tx {
        RepoReindexes.selectAll()
            .where { inFlight() or failedSince(since) }
            .orderBy(RepoReindexes.id, SortOrder.DESC)
            .limit(limit)
            .map(ReindexRun::of)
    }

    /** How many runs are queued or running now, and how many failed at or after [since] (epoch ms), across every account. */
    suspend fun recentCounts(since: Long): RecentRunCounts = db.tx {
        RecentRunCounts(
            inFlight = RepoReindexes.selectAll().where { inFlight() }.count(),
            failed = RepoReindexes.selectAll().where { failedSince(since) }.count(),
        )
    }

    private fun inFlight(): Op<Boolean> = RepoReindexes.state inList ReindexState.entries.filter { it.active }.map { it.code }

    /** Failed at or after [since]; a run failed without an end time (none should be) counts by when it was requested. */
    private fun failedSince(since: Long): Op<Boolean> =
        (RepoReindexes.state eq ReindexState.FAILED.code) and
            ((RepoReindexes.finishedAt greaterEq since) or
                (RepoReindexes.finishedAt.isNull() and (RepoReindexes.requestedAt greaterEq since)))

    /**
     * Runs until [scope] is cancelled: first marks runs a previous process left running as failed (`interrupted`), then starts queued runs
     * oldest first as permits free up in [scope] (the runs outlive a round), and prunes runs older than [KEEP] now and then. Each round is
     * reported to [worker] (dev/worker-telemetry); a run's own failure is its row's, not the worker's.
     */
    suspend fun run(scope: CoroutineScope, worker: WorkerHandle = WorkerHandle.None): Nothing {
        var recovered = worker.attempt(log, "Marking interrupted reindexes") { recoverInterrupted() } != null
        var lastPrune = 0L
        while (true) {
            if (!recovered) recovered = worker.attempt(log, "Marking interrupted reindexes") { recoverInterrupted() } != null
            var ok = worker.attempt(log, "Starting queued reindexes") { launchDue(scope) } != null
            val now = clock.now().toEpochMilliseconds()
            if (now - lastPrune >= PRUNE_INTERVAL.inWholeMilliseconds) {
                ok = worker.attempt(log, "Pruning old reindexes") { prune() } != null && ok
                lastPrune = now
            }
            if (ok && recovered) worker.success()
            withTimeoutOrNull(POLL_INTERVAL) { wakeups.receive() }
        }
    }

    /** Starts what's due and waits for it. Exposed for tests. */
    suspend fun runDue(scope: CoroutineScope) {
        launchDue(scope).forEach { it.join() }
    }

    /** Runs left `running` by a process that stopped: they can't be resumed, so they're failed as interrupted. Queued ones stay. */
    suspend fun recoverInterrupted() = db.tx {
        RepoReindexes.update(
            { (RepoReindexes.state eq ReindexState.RUNNING.code) and (RepoReindexes.id notInList running.keys.toList()) },
        ) {
            it[state] = ReindexState.FAILED.code
            it[finishedAt] = clock.now().toEpochMilliseconds()
            it[errorCode] = "interrupted"
            it[errorMessage] = "The AppView stopped while this ran"
        }
    }

    /** Drops runs requested more than [KEEP] ago, unless still active. */
    suspend fun prune() = db.tx {
        val cutoff = (clock.now() - KEEP).toEpochMilliseconds()
        RepoReindexes.deleteWhere { (requestedAt less cutoff) and (state notInList ACTIVE) }
    }

    private suspend fun launchDue(scope: CoroutineScope): List<Job> {
        val free = limits.parallelism - running.size
        if (free <= 0) return emptyList()
        val busy = running.values.map { it.value }.toSet()
        val due = db.tx {
            RepoReindexes.select(RepoReindexes.id, RepoReindexes.did)
                .where { RepoReindexes.state eq ReindexState.QUEUED.code }
                .orderBy(RepoReindexes.id)
                .map { it[RepoReindexes.id] to Did(it[RepoReindexes.did]) }
        }
        // Oldest first, one per account, none for an account already running, at most [free].
        val picked = due.filter { (_, did) -> did.value !in busy }.distinctBy { (_, did) -> did }.take(free)
        return picked.map { (id, did) ->
            running[id] = did
            scope.launch {
                try {
                    execute(id, did)
                } finally {
                    running.remove(id)
                    wakeups.trySend(Unit)
                }
            }
        }
    }

    /** One run, isolated: whatever happens is recorded on its row, never thrown. */
    private suspend fun execute(id: Long, did: Did) {
        val resync = db.tx {
            RepoReindexes.update({ (RepoReindexes.id eq id) and (RepoReindexes.state eq ReindexState.QUEUED.code) }) {
                it[state] = ReindexState.RUNNING.code
                it[startedAt] = clock.now().toEpochMilliseconds()
            }
            RepoReindexes.select(RepoReindexes.resyncSpaces).where { RepoReindexes.id eq id }.single()[RepoReindexes.resyncSpaces]
        }
        val outcome: Outcome =
            try {
                withTimeout(limits.timeout) { reindex(id, did) }
                Outcome(ReindexState.SUCCEEDED)
            } catch (@Suppress("SwallowedException") e: TimeoutCancellationException) {
                // Recorded as the run's outcome: the limit, not the exception, is what an operator needs.
                Outcome(ReindexState.FAILED, "timeout", "The run took longer than ${limits.timeout}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepoUnavailableException) {
                Outcome(ReindexState.UNAVAILABLE, e.code, e.message)
            } catch (e: RepoFormatException) {
                Outcome(ReindexState.FAILED, e.code, e.message)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The network, the database: the exception's class says which, its message may carry anything, so it's only logged.
                log.warn("Reindexing {} failed", did, e)
                Outcome(ReindexState.FAILED, "error", "Failed with ${e::class.simpleName}")
            }
        val spaces =
            resyncSpaces
                ?.takeIf { resync }
                ?.let { resyncOf ->
                    update(id) { it[phase] = ReindexPhase.SPACES.code }
                    WorkerHandle.None.attempt(log, "Resyncing $did's spaces") { resyncOf(did) }
                }
        update(id) {
            it[state] = outcome.state.code
            it[phase] = null
            it[finishedAt] = clock.now().toEpochMilliseconds()
            it[errorCode] = outcome.code
            it[errorMessage] = outcome.message?.take(MAX_MESSAGE)
            it[spacesResynced] = spaces
        }
        log.info("Reindex {} of {}: {}{}", id, did, outcome.state.code, outcome.code?.let { " ($it)" }.orEmpty())
    }

    private class Outcome(val state: ReindexState, val code: String? = null, val message: String? = null)

    /** Resolve, fetch, verify, apply, remove. Throws on anything that stops the run. */
    @Suppress("ThrowsCount") // Each way the account's identity can't be used is recorded with its own reason.
    private suspend fun reindex(id: Long, did: Did) {
        update(id) { it[phase] = ReindexPhase.RESOLVING.code }
        val doc = runCatching {
            resolveDid(did)
        }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                throw RepoUnavailableException("did_unresolvable", "The account's DID document couldn't be resolved")
            }
        val pds = doc.pdsEndpoint?.trimEnd('/') ?: throw RepoUnavailableException("no_pds", "The account's DID document names no PDS")
        val key =
            doc.signingKey?.let { runCatching { Multikey.decode(it) }.getOrNull() }
                ?: throw RepoUnavailableException("no_signing_key", "The account's DID document has no usable signing key")

        // Before the fetch: any commit Jetstream delivers from here on is ordered against the snapshot.
        val watch = indexer.watch(did)
        try {
            update(id) { it[phase] = ReindexPhase.FETCHING.code }
            fetch(pds, did).use { car ->
                update(id) { it[carBytes] = car.byteCount }
                apply(id, did, RepoSnapshot.open(car, did, key), watch)
            }
            db.tx { Actors.update({ Actors.did eq did.value }) { it[bskyBlocksSyncedAt] = clock.now().toEpochMilliseconds() } }
        } finally {
            indexer.unwatch(watch, limits.watchGrace)
        }
    }

    /** The account's repo as a CAR spooled to disk, within the limits. The guarded client's read timeout bounds a stalled PDS. */
    private suspend fun fetch(pds: String, did: Did): CarFile =
        http
            .prepareGet("$pds/xrpc/$GET_REPO") { parameter("did", did.value) }
            .execute { response ->
                if (response.status != HttpStatusCode.OK) throw refusal(response.status, runCatching { response.bodyAsText() }.getOrNull())
                withContext(io) {
                    CarFile.read(response.bodyAsChannel().toInputStream(), CarLimits(limits.maxCarBytes, limits.maxBlocks), tempDir)
                }
            }

    /** What a PDS's error answer to `getRepo` means for the run. Only the error name is read; its message is the PDS's, so never kept. */
    private fun refusal(status: HttpStatusCode, body: String?): Exception {
        val error = body?.let { runCatching { AtJson.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() }
        return when (error) {
            "RepoTakendown" -> RepoUnavailableException("repo_takendown", "The PDS answered that the repo is taken down")
            "RepoSuspended" -> RepoUnavailableException("repo_suspended", "The PDS answered that the repo is suspended")
            "RepoDeactivated" -> RepoUnavailableException("repo_deactivated", "The PDS answered that the repo is deactivated")
            "RepoNotFound" -> RepoUnavailableException("repo_not_found", "The PDS answered that it has no such repo")
            else -> RepoFormatException("pds_error", "The PDS answered getRepo with ${status.value}")
        }
    }

    /**
     * Two passes over the verified snapshot. The first reads the whole tree and every block it would index, so a repo missing a block or
     * holding a block that doesn't match its CID fails before anything changes. The second writes what changed, then deletes what's gone.
     */
    private suspend fun apply(id: Long, did: Did, snapshot: RepoSnapshot, watch: ReindexWatch) {
        watch.snapshotRev = snapshot.rev
        update(id) {
            it[rev] = snapshot.rev.take(REV_LENGTH)
            it[phase] = ReindexPhase.APPLYING.code
        }
        val held = indexer.repoHoldings(did)
        val indexed = Nsid.indexedCollections.toSet()
        // A block that's missing or doesn't match its CID means the repo isn't the commit's: every one this run would write is read and
        // checked first (and dropped), so such a repo fails before anything changes.
        val (entries, changed) =
            withContext(io) {
                val entries = snapshot.entries().filter { it.collection in indexed }.toList()
                val changed = entries.filter { changedSince(held, it) }
                changed.forEach(snapshot::verify)
                entries to changed
            }

        var counts = ReindexCounts(seen = entries.size, unchanged = entries.size - changed.size)
        update(id) { it.counts(counts) }
        changed.forEachIndexed { i, entry ->
            // A record that doesn't decode is dropped, as the indexer drops any such record.
            val record = withContext(io) { runCatching { snapshot.record(entry) }.getOrNull() }
            val result =
                record?.let { indexer.reindexWrite(did, entry.collection, entry.rkey, entry.cid, it, watch) } ?: RepoWriteResult.INVALID
            counts =
                when (result) {
                    RepoWriteResult.WRITTEN -> counts.copy(indexed = counts.indexed + 1)
                    RepoWriteResult.HELD -> counts.copy(live = counts.live + 1)
                    RepoWriteResult.INVALID -> counts.copy(invalid = counts.invalid + 1)
                }
            if ((i + 1) % PROGRESS_EVERY == 0) update(id) { it.counts(counts) }
        }

        update(id) {
            it.counts(counts)
            it[phase] = ReindexPhase.REMOVING.code
        }
        val inRepo = entries.groupBy({ it.collection }, { it.rkey }).mapValues { it.value.toSet() }
        for ((collection, rkeys) in held) {
            for (rkey in rkeys.keys - inRepo[collection].orEmpty()) {
                counts =
                    if (indexer.reindexDelete(did, collection, rkey, watch)) counts.copy(removed = counts.removed + 1)
                    else counts.copy(live = counts.live + 1)
            }
        }
        update(id) { it.counts(counts) }
    }

    /**
     * Whether [entry] must be written: it isn't held, or is held at another CID. A collection whose index keeps no CID (likes, follows,
     * reposts, blocks…) can't say, so a held record of one counts as unchanged, except the profile, a single record that is edited in
     * place, which is always written.
     */
    private fun changedSince(held: Map<String, Map<String, String?>>, entry: RepoEntry): Boolean {
        val collection = held[entry.collection].orEmpty()
        val cid = collection[entry.rkey]
        return when {
            entry.rkey !in collection -> true
            cid == null -> entry.collection == Nsid.ACTOR_PROFILE
            else -> cid != entry.cid
        }
    }

    private fun UpdateStatement.counts(counts: ReindexCounts) {
        this[RepoReindexes.recordsSeen] = counts.seen
        this[RepoReindexes.recordsUnchanged] = counts.unchanged
        this[RepoReindexes.recordsIndexed] = counts.indexed
        this[RepoReindexes.recordsInvalid] = counts.invalid
        this[RepoReindexes.recordsRemoved] = counts.removed
        this[RepoReindexes.recordsLive] = counts.live
    }

    private suspend fun update(id: Long, body: RepoReindexes.(UpdateStatement) -> Unit) = db.tx {
        RepoReindexes.update({ RepoReindexes.id eq id }, body = body)
    }

    companion object {
        private const val GET_REPO = "com.atproto.sync.getRepo"
        private val ACTIVE = listOf(ReindexState.QUEUED.code, ReindexState.RUNNING.code)

        /** Runs are kept at least this long (admin/repo-reindex). */
        val KEEP = 90.days
        private val PRUNE_INTERVAL = 1.days
        private val POLL_INTERVAL = 1.minutes
        private const val DEFAULT_RUNS = 20
        private const val RECENT_RUNS = 50
        private const val PROGRESS_EVERY = 1000
        private const val MAX_MESSAGE = 512
        private const val REV_LENGTH = 32
    }
}

/** The account's repo can't be had: refused by its PDS, or its identity doesn't resolve. Nothing is changed. */
internal class RepoUnavailableException(val code: String, message: String) : Exception(message)
