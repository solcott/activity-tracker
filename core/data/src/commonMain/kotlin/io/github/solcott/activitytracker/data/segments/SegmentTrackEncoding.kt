package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackPiece
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.PrivacyCircle
import kotlin.math.roundToInt
import kotlin.time.Instant

private const val CM_PER_M = 100

/**
 * [pieces] (a track already trimmed by the athlete's zones) as a segment track's data: positions in 1e-7 degrees, times in whole seconds
 * since [origin] (the first point of the activity's full track, so no wall-clock time is kept), and altitudes in centimeters, left out when
 * no point has one.
 */
fun segmentTrackData(pieces: List<List<LocationSample>>, origin: Instant): SegmentTrackData {
    val withAltitude = pieces.any { piece -> piece.any { it.altitude != null } }
    return SegmentTrackData(
        pieces.map { piece ->
            SegmentTrackPiece(
                lat = piece.map { PrivacyCircle.toE7(it.latitude) },
                lng = piece.map { PrivacyCircle.toE7(it.longitude) },
                t = piece.map { (it.time - origin).inWholeSeconds },
                alt = if (withAltitude) piece.map { s -> s.altitude?.let { (it * CM_PER_M).roundToInt() } } else null,
            )
        }
    )
}
