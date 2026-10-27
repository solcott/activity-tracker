package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Instant

/** What happens to a recording, in order: what [RecordingState.apply] folds and the journal keeps. */
sealed interface RecordingEvent {
    val at: Instant

    /** An accepted position. */
    data class Sample(val sample: LocationSample) : RecordingEvent {
        override val at: Instant
            get() = sample.time
    }

    data class Paused(override val at: Instant) : RecordingEvent

    /** A resume starts a new segment: nothing is measured across it. */
    data class Resumed(override val at: Instant) : RecordingEvent
}

/**
 * Live recording state, folded from [RecordingEvent]s ([apply]) so a recording rebuilt from its journal is exactly what the athlete saw.
 * Totals count only what was recorded while not paused: each segment (from a start or a resume) is measured from its own first sample.
 */
data class RecordingState(
    val status: Status = Status.Idle,
    val startedAt: Instant? = null,
    /** Accumulated time while not paused, excluding the current running segment. */
    val activeBeforeSegment: Duration = Duration.ZERO,
    val segmentStartedAt: Instant? = null,
    val distanceMeters: Double = 0.0,
    /**
     * Time spent moving, in milliseconds, summed exactly: fixes are rarely a whole second apart, so whole seconds per step would lose most
     * of it.
     */
    val movingMillis: Long = 0,
    val elevationGainMeters: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    /** The current segment: 0 from the start, one more at each resume. */
    val segment: Int = 0,
    /** The current segment's last accepted sample; null at its start, so nothing is measured from before a pause. */
    val last: LocationSample? = null,
    /** The altitude climbs are measured from, moved only past [ELEVATION_HYSTERESIS_M] so GPS jitter doesn't add up. */
    val climbBase: Double? = null,
    val sampleCount: Int = 0,
    /** The account recording it; null while idle. */
    val did: String? = null,
    /** Rebuilt from the journal after the app stopped, not yet resumed: the athlete chooses to resume, save or discard it. */
    val recovered: Boolean = false,
    /** The last change couldn't be saved to the device (a full disk, say), so it wasn't applied either. */
    val saveFailed: Boolean = false,
) {
    enum class Status {
        Idle,
        Recording,
        Paused,
    }

    /** [movingMillis] in whole seconds, rounded once. */
    val movingSeconds: Long
        get() = (movingMillis + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND

    /** Whether this is [did]'s recording, recorded or paused, so signing in as [did] belongs on the record screen. */
    fun inProgressFor(did: String): Boolean = status != Status.Idle && this.did == did

    fun elapsed(now: Instant): Duration = activeBeforeSegment + (segmentStartedAt?.let { now - it } ?: Duration.ZERO)

    /** This state after [event]. Pure, so the recorder and a replay of the journal agree. */
    fun apply(event: RecordingEvent): RecordingState =
        when (event) {
            is RecordingEvent.Sample -> if (status == Status.Recording) accept(event.sample) else this
            is RecordingEvent.Paused ->
                if (status != Status.Recording) this
                else copy(status = Status.Paused, activeBeforeSegment = elapsed(event.at), segmentStartedAt = null, last = null)
            is RecordingEvent.Resumed ->
                if (status != Status.Paused) this
                else
                    copy(
                        status = Status.Recording,
                        segmentStartedAt = event.at,
                        segment = segment + 1,
                        last = null,
                        climbBase = null,
                        recovered = false,
                    )
        }

    private fun accept(sample: LocationSample): RecordingState {
        val previous = last
        val step = previous?.let { TrackMath.distanceMeters(it, sample) } ?: 0.0
        // A fix dated before the last (a clock adjustment) counts no time.
        val millis = previous?.let { (sample.time - it.time).inWholeMilliseconds }?.coerceAtLeast(0) ?: 0
        val speed = sample.speed ?: if (millis > 0) step * MILLIS_PER_SECOND / millis else 0.0
        val altitude = sample.altitude
        val base = climbBase
        val climb = if (altitude != null && base != null && altitude - base > ELEVATION_HYSTERESIS_M) altitude - base else 0.0
        val nextBase = if (altitude != null && (base == null || abs(altitude - base) > ELEVATION_HYSTERESIS_M)) altitude else base
        return copy(
            distanceMeters = distanceMeters + step,
            movingMillis = movingMillis + if (speed > MOVING_SPEED_MPS) millis else 0,
            elevationGainMeters = elevationGainMeters + climb,
            maxSpeedMps = maxOf(maxSpeedMps, speed),
            last = sample,
            climbBase = nextBase,
            sampleCount = sampleCount + 1,
        )
    }

    companion object {
        const val MAX_ACCURACY_M = 50.0
        private const val MOVING_SPEED_MPS = 0.5
        private const val ELEVATION_HYSTERESIS_M = 3.0
        private const val MILLIS_PER_SECOND = 1000L

        /** A recording started at [startedAt] by [did]. */
        fun started(did: String?, startedAt: Instant) =
            RecordingState(status = Status.Recording, startedAt = startedAt, segmentStartedAt = startedAt, did = did)

        /**
         * A recording rebuilt from its [events]: paused at its last event if it was recording then, so the time since counts as paused once
         * it's resumed.
         */
        fun replay(did: String, startedAt: Instant, events: List<RecordingEvent>): RecordingState {
            val folded = events.fold(started(did, startedAt)) { state, event -> state.apply(event) }
            val end = events.lastOrNull()?.at ?: startedAt
            return folded.apply(RecordingEvent.Paused(end)).copy(recovered = true)
        }
    }
}

/** A finished recording: its state, when it ended, and every accepted sample in order. */
data class RecordingResult(val state: RecordingState, val endedAt: Instant, val samples: List<LocationSample> = emptyList())
