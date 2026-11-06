package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.di.SpacesSupport
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.lexicon.AppCollections
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.atproto.ApplyWrites
import io.github.solcott.activitytracker.lexicon.atproto.DescribeRepo
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far a leave has got (auth/leaving), in the order it runs. */
enum class LeaveStep {
    /** This device's queued writes, import, recording and export are being discarded. */
    Discard,

    /** The athlete's spaces are being deleted, `self` first. */
    Spaces,

    /** The app's records are being deleted from the repo. */
    Repo,

    /** The account's data on this device is being cleared, and the session ended. */
    Local,
}

/**
 * The athlete's own segments and dive sites, as leaving treats them (auth/leaving): [activeSegments] and [activeSites] others use stay,
 * republished without their name; the [otherSegments] and [otherSites] are deleted.
 */
data class SharedPlaces(val activeSegments: Int, val otherSegments: Int, val activeSites: Int, val otherSites: Int)

/** What the leave screen shows while a leave runs: its [step], how many repo records it has deleted, and what stopped it, if anything. */
data class LeaveProgress(
    val step: LeaveStep,
    val recordsDeleted: Int = 0,
    /** Until when the PDS asked it to wait, while it does. */
    val pausedUntil: Instant? = null,
    /** The refusal that stopped the leave; [LeaveJob.run] again retries from where it stopped. */
    val error: Throwable? = null,
)

/**
 * Leaves Activity Tracker (auth/leaving): removes everything the app put in the athlete's atproto account and keeps the account. In order,
 * each step recorded on the device ([AccountMarks.leaving]) before it starts, so a restart resumes rather than reopening the app: discard
 * this device's queued writes, import, recording and export; delete each of the athlete's spaces, `self` first, so other devices stop
 * (`SessionClients.checkStillHere`); delete every record of an app collection from the repo ([AppCollections] plus any other
 * `app.activitytracker` collection `describeRepo` lists, so retired ones go too), in `applyWrites` batches, waiting when the PDS asks; then
 * clear the account here and end the session with the "left" note. Every step lists before deleting, so repeating one is harmless.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions") // Each part of the account that leaving clears, and each step of it.
class LeaveJob(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val marks: AccountMarks,
    private val auth: AuthRepository,
    private val recorder: ActivityRecorder,
    private val saves: ImportSaves,
    private val exports: DataExports,
    logger: Logger,
    private val clock: Clock = Clock.System,
) {
    private val log = logger.withTag("LeaveJob")
    private val did = clients.did
    private val lock = Mutex()

    /** Where a leave runs: not a screen's scope, since the app shows only the leave once it starts, and resumes it after a restart. */
    @Suppress("InjectDispatcher") // Like ImportSaves': work that outlives the screen that started it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The running leave's progress; null when none has started on this device. */
    val progress: StateFlow<LeaveProgress?>
        field = MutableStateFlow<LeaveProgress?>(null)

    /** Whether a leave of this account started on this device and hasn't finished. */
    suspend fun inProgress(): Boolean = marks.leaving(did) != null

    /** Whether a data export of this account is running or paused, which leaving waits for (it would read records as they go). */
    suspend fun exportRunning(): Boolean =
        exports.current(did)?.state.let { it == DataExportEntity.STATE_RUNNING || it == DataExportEntity.STATE_PAUSED }

    /** Starts leaving: records it on the device first, so nothing is discarded unless it then runs to the end, here or after a restart. */
    suspend fun start() {
        if (marks.leaving(did) == null) marks.setLeaving(did, LeaveStep.Discard.name)
        run()
    }

    /**
     * Starts leaving (auth/leaving) and runs it in the background: records it on the device first, which turns the app over to the leave
     * screen, then runs it there.
     */
    suspend fun begin() {
        if (marks.leaving(did) == null) {
            // Nothing is discarded unless the leave can run: an export would read records as they go, and an unreachable PDS would leave
            // this device's work discarded with nothing deleted. Each throws before the leave is recorded.
            if (exportRunning()) throw ExportRunningException()
            clients.pds.query(DescribeRepo.method, DescribeRepo.Params(did.value))
            marks.setLeaving(did, LeaveStep.Discard.name)
        }
        resume()
    }

    /** Thrown by [begin] while a data export of the account is running or paused (auth/leaving): leaving waits for it. */
    class ExportRunningException : IllegalStateException("A data export is still running")

    /** Runs the recorded leave in the background, unless it's running already: after a restart, or to retry after a refusal. */
    fun resume() {
        if (lock.isLocked) return
        scope.launch { run() }
    }

    /**
     * After the athlete left on another device and chose to discard what this one holds (auth/leaving): drops this device's queued writes,
     * import, recording and export, and ends the session with the "left" note.
     */
    suspend fun discardLeftElsewhere() =
        withContext(NonCancellable) {
            discard()
            db.feed().clearAll()
            marks.forgetSpaces(did)
            auth.left()
        }

    /**
     * The athlete's own segments and dive sites, counted by whether they're active (auth/leaving): listed from their repo and looked up in
     * the AppView one by one. One the AppView doesn't serve counts as not active.
     */
    suspend fun sharedPlaces(): SharedPlaces {
        suspend fun uris(collection: String): List<AtUri> {
            val all = mutableListOf<AtUri>()
            var cursor: String? = null
            do {
                val page =
                    clients.pds.query(ListRecords.method, ListRecords.Params(did.value, collection, limit = PAGE_SIZE, cursor = cursor))
                all += page.records.map { it.uri }
                cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
            } while (cursor != null)
            return all
        }
        val segments =
            uris(Nsid.FEED_SEGMENT).map { uri ->
                runSuspendCatching { clients.appView.query(GetSegment.method, GetSegment.Params(uri)).segment.status }.getOrNull()
            }
        val sites =
            uris(Nsid.FEED_DIVE_SITE).map { uri ->
                runSuspendCatching { clients.appView.query(GetDiveSite.method, GetDiveSite.Params(uri)).site.status }.getOrNull()
            }
        return SharedPlaces(
            activeSegments = segments.count { it == SegmentStatus.ACTIVE },
            otherSegments = segments.count { it != SegmentStatus.ACTIVE },
            activeSites = sites.count { it == SegmentStatus.ACTIVE },
            otherSites = sites.count { it != SegmentStatus.ACTIVE },
        )
    }

    /** Runs the recorded leave from its step to the end; a refusal stops it with [LeaveProgress.error], and running again retries. */
    suspend fun run() = lock.withLock {
        val recorded = marks.leaving(did)?.let { name -> LeaveStep.entries.firstOrNull { it.name == name } } ?: return@withLock
        var deleted = 0
        try {
            for (step in LeaveStep.entries.dropWhile { it != recorded }) {
                marks.setLeaving(did, step.name)
                progress.value = LeaveProgress(step, deleted)
                when (step) {
                    LeaveStep.Discard -> discard()
                    LeaveStep.Spaces -> deleteSpaces()
                    LeaveStep.Repo -> deleted = deleteRepoRecords { deleted = it }
                    LeaveStep.Local -> finish()
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.w(e) { "Leaving stopped at ${progress.value?.step}" }
            progress.value = progress.value?.copy(error = e, pausedUntil = null)
        }
    }

    private suspend fun discard() {
        db.uploads().discardAll(did.value)
        db.keyValue().delete("uploads.importsPaused.${did.value}")
        if (recorder.state.value.status != RecordingState.Status.Idle) recorder.discard()
        saves.signedOut(did)
        exports.cancel(did)
    }

    private suspend fun deleteSpaces() {
        // A PDS without Spaces holds none of the athlete's spaces, and has no deleteSpace to call.
        if (clients.spacesSupport.value == SpacesSupport.Unsupported) return
        for (skey in SPACES_SELF_FIRST + (marks.knownSpaces(did) - SPACES_SELF_FIRST.toSet()).sorted()) {
            clients.spaces.deleteSpace(clients.tiers.uri(skey))
        }
    }

    /** Deletes every app record from the repo, reporting the running count to [onDeleted]; returns the total. */
    private suspend fun deleteRepoRecords(onDeleted: (Int) -> Unit): Int {
        val listed = clients.pds.query(DescribeRepo.method, DescribeRepo.Params(did.value)).collections
        val collections = (AppCollections.records + listed.filter { it.startsWith("${Nsid.NAMESPACE}.") }).distinct()
        var deleted = 0
        for (collection in collections) {
            val rkeys = mutableListOf<String>()
            var cursor: String? = null
            do {
                val page = paced {
                    clients.pds.query(ListRecords.method, ListRecords.Params(did.value, collection, limit = PAGE_SIZE, cursor = cursor))
                }
                rkeys += page.records.mapNotNull { it.uri.rkey }
                cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
            } while (cursor != null)
            for (batch in rkeys.chunked(ApplyWrites.MAX_WRITES)) {
                paced {
                    clients.pds.procedure(
                        ApplyWrites.method,
                        ApplyWrites.Input(did, batch.map { ApplyWrites.delete(collection, it) }),
                    )
                }
                deleted += batch.size
                onDeleted(deleted)
                progress.value = progress.value?.copy(recordsDeleted = deleted)
            }
        }
        return deleted
    }

    /** Runs [call], waiting out each rate limit the PDS answers with (as long as it asks, at least [MIN_PAUSE]) and trying again. */
    private suspend fun <T> paced(call: suspend () -> T): T {
        while (true) {
            try {
                return call()
            } catch (e: XrpcException) {
                if (e.status != HTTP_TOO_MANY_REQUESTS) throw e
                val now = clock.now()
                val until = maxOf(e.retryAfter ?: e.rateLimit?.resetAt ?: (now + 1.minutes), now + MIN_PAUSE)
                clients.pacing.pause(until)
                progress.value = progress.value?.copy(pausedUntil = until)
                delay(until - now)
                progress.value = progress.value?.copy(pausedUntil = null)
            }
        }
    }

    private suspend fun finish() =
        withContext(NonCancellable) {
            db.uploads().discardAll(did.value)
            db.feed().clearAll()
            marks.forgetSpaces(did)
            marks.clearLeaving(did)
            auth.left()
        }

    private companion object {
        val SPACES_SELF_FIRST = listOf(AthleteSpace.SELF, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.INTERACTIONS)
        const val PAGE_SIZE = 100
        const val HTTP_TOO_MANY_REQUESTS = 429
        val MIN_PAUSE = 10.seconds
    }
}
