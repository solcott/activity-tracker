package io.github.solcott.activitytracker.data.activity.trim

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Instant

/** The part of an activity a trim keeps (feed/activity-trim), in whole seconds since its start: [from] to [to], both kept. */
@Immutable
data class KeptSpan(val from: Int, val to: Int) {
    init {
        require(from >= 0 && to >= from) { "A kept span runs forward from the activity's start" }
    }

    /** How long it is. */
    val seconds: Int
        get() = to - from

    /** Whether this keeps the whole of an activity [elapsed] seconds long, so there's nothing to trim. */
    fun isWhole(elapsed: Int): Boolean = from <= 0 && to >= elapsed

    /** Whether this keeps the whole of [input], over its samples' clock time ([TrimInput.endSeconds]). */
    fun isWhole(input: TrimInput): Boolean = isWhole(input.endSeconds)

    /**
     * The elapsed time [input] has left once cut to this span: its elapsed time less the clock time cut, never more than the span. A
     * recording's elapsed time leaves out its pauses while its samples' times don't, so a span can't simply be its new elapsed time.
     */
    fun keptElapsed(input: TrimInput): Int = (input.elapsedSeconds - (input.endSeconds - seconds)).coerceIn(0, seconds)

    companion object {
        /** The least a trim keeps: the handles never come closer than this. */
        const val MIN_SECONDS = 60

        /** All of an activity [elapsed] seconds long. */
        fun whole(elapsed: Int): KeptSpan = KeptSpan(0, elapsed.coerceAtLeast(0))
    }
}

/** What a trim works over: an activity's start, its length in seconds, its streams and its full-resolution route (empty without GPS). */
class TrimInput(val startedAt: Instant, val elapsedSeconds: Int, val streams: ActivityStreamData?, val route: List<LocationSample>) {
    /** Whether a span can be chosen by distance: the streams or the route say how far the athlete went. */
    val hasDistance: Boolean
        get() = TrimSeries.of(this)?.distance != null

    /**
     * Seconds from the start to the end of the recording, the timeline a [KeptSpan] is on: the elapsed time, or the last sample's time when
     * that's later (a recording paused along the way, whose elapsed time leaves the pauses out).
     */
    val endSeconds: Int by lazy { maxOf(elapsedSeconds, TrimSeries.of(this)?.time?.lastOrNull() ?: 0) }
}
