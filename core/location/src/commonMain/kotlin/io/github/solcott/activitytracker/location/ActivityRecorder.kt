package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live recording state. Stats are recomputed incrementally as samples arrive. */
data class RecordingState(
    val status: Status = Status.Idle,
    val startedAt: Instant? = null,
    /** Accumulated time while not paused, excluding the current running segment. */
    val activeBeforeSegment: Duration = Duration.ZERO,
    val segmentStartedAt: Instant? = null,
    val samples: List<LocationSample> = emptyList(),
    val distanceMeters: Double = 0.0,
    val movingSeconds: Long = 0,
    val elevationGainMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
) {
    enum class Status {
        Idle,
        Recording,
        Paused,
    }

    fun elapsed(now: Instant): Duration = activeBeforeSegment + (segmentStartedAt?.let { now - it } ?: Duration.ZERO)
}

data class RecordingResult(val state: RecordingState, val endedAt: Instant)

/**
 * App-scoped so a recording survives navigation. Samples with poor accuracy are dropped; distance and moving time only accumulate while
 * recording (not paused).
 */
@SingleIn(AppScope::class)
@Inject
class ActivityRecorder(
    private val tracker: LocationTracker,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Recording outlives any screen, so it runs in the recorder's own scope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastAltitude: Double? = null

    fun start() {
        if (_state.value.status != RecordingState.Status.Idle) return
        val now = clock.now()
        _state.value =
            RecordingState(
                status = RecordingState.Status.Recording,
                startedAt = now,
                segmentStartedAt = now,
            )
        lastAltitude = null
        job = scope.launch { tracker.updates().collect(::onSample) }
    }

    fun pause() = _state.update {
        if (it.status != RecordingState.Status.Recording) return@update it
        val now = clock.now()
        it.copy(
            status = RecordingState.Status.Paused,
            activeBeforeSegment = it.elapsed(now),
            segmentStartedAt = null,
        )
    }

    fun resume() = _state.update {
        if (it.status != RecordingState.Status.Paused) {
            it
        } else {
            it.copy(status = RecordingState.Status.Recording, segmentStartedAt = clock.now())
        }
    }

    fun stop(): RecordingResult? {
        val current = _state.value
        if (current.status == RecordingState.Status.Idle) return null
        job?.cancel()
        job = null
        val now = clock.now()
        _state.value = RecordingState()
        return RecordingResult(
            current.copy(activeBeforeSegment = current.elapsed(now), segmentStartedAt = null),
            now,
        )
    }

    internal fun onSample(sample: LocationSample) = _state.update { state ->
        if (state.status != RecordingState.Status.Recording) return@update state
        if ((sample.accuracy ?: 0.0) > MAX_ACCURACY_M) return@update state
        val previous = state.samples.lastOrNull()
        val step = previous?.let { TrackMath.distanceMeters(it, sample) } ?: 0.0
        val seconds = previous?.let { (sample.time - it.time).inWholeSeconds } ?: 0
        val speed = sample.speed ?: if (seconds > 0) step / seconds else 0.0
        var gain = state.elevationGainMeters
        sample.altitude?.let { alt ->
            val base = lastAltitude
            if (base == null) {
                lastAltitude = alt
            } else if (alt - base > ELEVATION_HYSTERESIS_M) {
                gain += alt - base
                lastAltitude = alt
            } else if (base - alt > ELEVATION_HYSTERESIS_M) {
                lastAltitude = alt
            }
        }
        state.copy(
            samples = state.samples + sample,
            distanceMeters = state.distanceMeters + step,
            movingSeconds = state.movingSeconds + if (speed > MOVING_SPEED_MPS) seconds else 0,
            elevationGainMeters = gain,
            maxSpeedMps = maxOf(state.maxSpeedMps, speed),
        )
    }

    private companion object {
        const val MAX_ACCURACY_M = 50.0
        const val MOVING_SPEED_MPS = 0.5
        const val ELEVATION_HYSTERESIS_M = 3.0
    }
}
