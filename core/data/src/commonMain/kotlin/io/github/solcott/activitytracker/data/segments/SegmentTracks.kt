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
 * segment matching or grouping is on ([TrackConsent]) and the zones were read this session, never untrimmed. One trimmed by the zones last
 * read rather than read fresh for the publish carries that revision, and is trimmed again by [SegmentTrackSweeper] once fresh zones differ
 * ([takeStale]); when none can be written, the sweep writes it later. Callers queue the activity and its full track first.
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentTracks(private val consent: TrackConsent, private val store: SegmentTrackStore) {
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
    ) = write(activity, route, trim, replacing, piecesOf(route, trim, combined, minPoints = 2))

    /**
     * Queues a trackless dive's segment track from its surface [fixes], its entry first, trimmed by [trim]: one is enough to visit a site.
     */
    suspend fun putSurfaceFixes(activity: AtUri, fixes: List<LocationSample>, trim: PublishTrim) =
        write(activity, fixes, trim, replacing = false, piecesOf(fixes, trim, combined = false, minPoints = 1))

    /**
     * [route] cut by [trim]'s settings when it has [minPoints] positions (at least one) and the athlete wants segment tracks, else null.
     */
    private suspend fun piecesOf(
        route: List<LocationSample>,
        trim: PublishTrim,
        combined: Boolean,
        minPoints: Int,
    ): List<List<LocationSample>>? {
        val known = trim.trim as? RouteTrim.Known
        return if (known != null && route.size >= maxOf(minPoints, 1) && wanted()) known.settings.pieces(route, combined) else null
    }

    private suspend fun write(
        activity: AtUri,
        route: List<LocationSample>,
        trim: PublishTrim,
        replacing: Boolean,
        pieces: List<List<LocationSample>>?,
    ) {
        when {
            !pieces.isNullOrEmpty() -> {
                store.put(activity, segmentTrackData(pieces, route.first().time), (trim.trim as? RouteTrim.Known)?.revision)
                if (!trim.fresh) stale = true
            }
            pieces != null -> store.delete(activity)
            replacing -> delete(activity)
        }
    }

    private suspend fun wanted(): Boolean = consent.canUse && consent.load() == TrackConsent.Wanted.YES

    /**
     * Queues deleting [activity]'s segment track whether either setting is on or off, since one may be left from when one was on. An
     * athlete who was never asked either has none, so nothing is queued for them.
     */
    suspend fun delete(activity: AtUri) {
        if (consent.canUse && consent.load() != TrackConsent.Wanted.NEVER) store.delete(activity)
    }
}
