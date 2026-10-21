package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.PublishTrim
import io.github.solcott.activitytracker.data.activity.RouteTrim
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.LocationSample

/**
 * Keeps each activity's segment track with its full track as it's published, combined and deleted. A segment track is queued only while
 * matching is on ([SegmentPrefsRepository]) and the zones were read this session, never untrimmed. One trimmed by the zones last read
 * rather than read fresh for the publish carries that revision, and is trimmed again by [SegmentTrackSweeper] once fresh zones differ
 * ([takeStale]); when none can be written, the sweep writes it later. Callers queue the activity and its full track first.
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentTracks(private val prefs: SegmentPrefsRepository, private val store: SegmentTrackStore) {
    private var stale = false

    /** Whether a segment track was queued this session by zones not read fresh, cleared by asking: the caller sweeps once they're read. */
    fun takeStale(): Boolean = stale.also { stale = false }

    /**
     * Queues [activity]'s segment track from [route], its full track, trimmed by [trim] (a [combined] route broken per member). When one
     * can't be written now and the activity may have one already ([replacing], as a rebuilt combined activity does), queues deleting it
     * instead, so a segment track never outlives the route it was made from.
     */
    suspend fun put(
        activity: AtUri,
        route: List<LocationSample>,
        trim: PublishTrim,
        combined: Boolean = false,
        replacing: Boolean = false,
    ) {
        val known = trim.trim as? RouteTrim.Known
        val pieces = if (known != null && route.size >= 2 && matchingOn()) known.settings.pieces(route, combined) else null
        when {
            !pieces.isNullOrEmpty() -> {
                store.put(activity, segmentTrackData(pieces, route.first().time), known?.revision)
                if (!trim.fresh) stale = true
            }
            pieces != null -> store.delete(activity)
            replacing -> delete(activity)
        }
    }

    private suspend fun matchingOn(): Boolean = prefs.canUse && prefs.load() == SegmentMatching.ON

    /**
     * Queues deleting [activity]'s segment track whether matching is on or off, since one may be left from when it was on. An athlete who
     * was never asked has none, so nothing is queued for them.
     */
    suspend fun delete(activity: AtUri) {
        if (prefs.canUse && prefs.load() != SegmentMatching.NEVER_ASKED) store.delete(activity)
    }
}
