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
}
