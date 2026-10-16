package io.github.solcott.activitytracker.location

import kotlin.time.Instant

private const val BISECTION_STEPS = 30

/**
 * [points] without the parts inside any of [circles] or within [endsMeters] of either end (measured along the track), cut where the track
 * crosses each boundary: the cut point is interpolated on the shown side, so none of the result is hidden. A middle pass through a zone
 * becomes a straight line from where the track entered to where it left. Empty when everything is hidden.
 */
fun TrackMath.trim(points: List<LocationSample>, circles: List<PrivacyCircle>, endsMeters: Double = 0.0): List<LocationSample> {
    if (points.isEmpty() || (circles.isEmpty() && endsMeters <= 0.0)) return points
    val along = DoubleArray(points.size)
    for (i in 1..points.lastIndex) along[i] = along[i - 1] + distanceMeters(points[i - 1], points[i])
    val total = along.last()

    fun hidden(latitude: Double, longitude: Double, distance: Double): Boolean =
        (endsMeters > 0.0 && (distance < endsMeters || total - distance < endsMeters)) || circles.any { it.contains(latitude, longitude) }

    val hidden = BooleanArray(points.size) { hidden(points[it].latitude, points[it].longitude, along[it]) }
    val result = mutableListOf<LocationSample>()
    for (i in points.indices) {
        if (i > 0 && hidden[i - 1] != hidden[i]) {
            result +=
                crossing(points[i - 1], points[i], hiddenAtEnd = hidden[i]) { p, t ->
                    hidden(p.latitude, p.longitude, along[i - 1] + (along[i] - along[i - 1]) * t)
                }
        }
        if (!hidden[i]) result += points[i]
    }
    return result
}

/**
 * Where the segment [a]–[b] crosses from shown to hidden (or back, when not [hiddenAtEnd]), found by bisection with [hiddenAt] (a point and
 * its fraction along the segment), on the shown side.
 */
private inline fun crossing(
    a: LocationSample,
    b: LocationSample,
    hiddenAtEnd: Boolean,
    hiddenAt: (LocationSample, Double) -> Boolean,
): LocationSample {
    var shown = if (hiddenAtEnd) 0.0 else 1.0
    var cover = 1.0 - shown
    repeat(BISECTION_STEPS) {
        val mid = (shown + cover) / 2
        if (hiddenAt(interpolate(a, b, mid), mid)) cover = mid else shown = mid
    }
    return interpolate(a, b, shown)
}

private fun interpolate(a: LocationSample, b: LocationSample, t: Double): LocationSample {
    fun mix(x: Double, y: Double) = x + (y - x) * t
    val millis = a.time.toEpochMilliseconds() + ((b.time.toEpochMilliseconds() - a.time.toEpochMilliseconds()) * t).toLong()
    return LocationSample(
        latitude = mix(a.latitude, b.latitude),
        longitude = mix(a.longitude, b.longitude),
        altitude = if (a.altitude != null && b.altitude != null) mix(a.altitude, b.altitude) else null,
        speed = null,
        accuracy = null,
        time = Instant.fromEpochMilliseconds(millis),
    )
}
