package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-scoped so a recording survives navigation. Samples with poor accuracy are dropped; distance and moving time only accumulate while
 * recording, each segment measured from its own first sample ([RecordingState.apply]).
 *
 * Every change (a sample, a pause, a resume) runs one at a time, in the order it was asked for, under [mutex]: a pause tapped while a
 * sample is being applied waits for it, so nothing is counted twice or measured across a pause. Each is saved to [journal] before it's
 * applied, so what [state] shows is never ahead of what a recovery rebuilds; a save that fails isn't applied and sets
 * [RecordingState.saveFailed]. The samples are kept in an append-only list rather than in [state], which holds only totals. [foreground]
 * hears every status change, so a platform can keep the app alive while a recording is in progress.
 */
@SingleIn(AppScope::class)
@Inject
@Suppress("TooManyFunctions") // The recorder's commands, and the ordering and saving every one goes through.
class ActivityRecorder(
    private val tracker: LocationTracker,
    private val clock: Clock,
    private val journal: RecordingJournal = RecordingJournal.None,
    private val foreground: RecordingForeground = RecordingForeground.None,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val state: StateFlow<RecordingState>
        field = MutableStateFlow(RecordingState())

    /** Recording outlives any screen, so it runs in the recorder's own scope, on one lane so commands start in the order they're made. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher.limitedParallelism(1))
    private val mutex = Mutex()

    /** Every accepted sample, in order. Only touched under [mutex]. */
    private val samples = mutableListOf<LocationSample>()
    private var collector: Job? = null

    /** The journal's id for the recording in progress; null when idle or not saved. */
    private var recordingId: Long? = null

    /** Starts recording for the account [did], unless a recording is already in progress, paused or recovered. */
    fun start(did: String? = null) {
        val now = clock.now()
        command {
            if (state.value.status != RecordingState.Status.Idle) return@command
            samples.clear()
            val id = did?.let { saving { journal.begin(it, now) } }
            if (did != null && id == null) {
                setState(RecordingState(saveFailed = true))
                return@command
            }
            recordingId = id
            setState(RecordingState.started(did, now))
            collect()
        }
    }

    fun pause() {
        val now = clock.now()
        command { record(RecordingEvent.Paused(now)) }
    }

    /** Resumes a paused recording, or a recovered one: the time since it stopped counts as paused. */
    fun resume() {
        val now = clock.now()
        command {
            if (state.value.status != RecordingState.Status.Paused) return@command
            if (record(RecordingEvent.Resumed(now))) collect()
        }
    }

    /**
     * Pauses the recording, stops listening for positions, and returns what was recorded, or null when nothing is. It stays saved and
     * paused until [finished] (its activity is queued) or [discard], so a publish that's refused loses nothing.
     */
    suspend fun finish(): RecordingResult? = mutex.withLock {
        if (state.value.status == RecordingState.Status.Idle) return null
        stopCollecting()
        val now = clock.now()
        record(RecordingEvent.Paused(now))
        RecordingResult(state.value, now, samples.toList())
    }

    /** Forgets the recording once its activity is queued for publishing. */
    suspend fun finished() = forget()

    /** Forgets the recording: nothing of it is left on the device. */
    suspend fun discard() = forget()

    /**
     * Offers back [did]'s unfinished recording, if it has one and nothing is recording now: rebuilt from the journal, paused at its last
     * event, [RecordingState.recovered] until it's resumed. Nothing listens for positions until then.
     */
    suspend fun recover(did: String) = mutex.withLock {
        if (state.value.status != RecordingState.Status.Idle) return@withLock
        val saved = saving { journal.unfinished(did) } ?: return@withLock
        samples.clear()
        samples += saved.samples
        recordingId = saved.id
        // It stopped at its last event; saving that pause keeps the gap out of its time however many more times it's recovered.
        if (saved.events.lastOrNull() !is RecordingEvent.Paused) {
            saving { journal.append(saved.id, RecordingEvent.Paused(saved.events.lastOrNull()?.at ?: saved.startedAt)) }
        }
        setState(RecordingState.replay(saved.did, saved.startedAt, saved.events))
    }

    /** Stops recording as its account signs out, keeping it saved to be offered back when that account signs in again. */
    suspend fun suspendForSignOut() = mutex.withLock {
        if (state.value.status == RecordingState.Status.Idle) return@withLock
        stopCollecting()
        record(RecordingEvent.Paused(clock.now()))
        samples.clear()
        recordingId = null
        setState(RecordingState())
    }

    /** Applies a sample from the tracker, unless it's too inaccurate. Exposed for tests. */
    internal suspend fun onSample(sample: LocationSample) = mutex.withLock {
        if ((sample.accuracy ?: 0.0) > RecordingState.MAX_ACCURACY_M) return@withLock
        record(RecordingEvent.Sample(sample))
    }

    /**
     * Saves [event] and applies it, keeping the sample if it was accepted; false when it changes nothing or couldn't be saved. Only called
     * under [mutex].
     */
    private suspend fun record(event: RecordingEvent): Boolean {
        val before = state.value
        val after = before.apply(event)
        val saved = after != before && recordingId.let { id -> id == null || saving { journal.append(id, event) } != null }
        when {
            saved -> {
                if (event is RecordingEvent.Sample) samples += event.sample
                setState(after.copy(saveFailed = false))
            }
            after != before -> setState(before.copy(saveFailed = true))
        }
        return saved
    }

    private suspend fun forget() = mutex.withLock {
        stopCollecting()
        recordingId?.let { id -> saving { journal.delete(id) } }
        recordingId = null
        samples.clear()
        setState(RecordingState())
    }

    private fun setState(next: RecordingState) {
        val previous = state.value.status
        state.value = next
        if (next.status != previous) foreground.statusChanged(next.status)
    }

    /** [block]'s result, or null when the journal failed, which [RecordingState.saveFailed] shows. Cancellation still propagates. */
    @Suppress("SwallowedException", "TooGenericExceptionCaught") // The failure is the state the athlete sees; the recording goes on.
    private suspend fun <T : Any> saving(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun collect() {
        if (collector?.isActive == true) return
        collector = scope.launch { tracker.updates().collect { onSample(it) } }
    }

    private fun stopCollecting() {
        collector?.cancel()
        collector = null
    }

    private fun command(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }
}
