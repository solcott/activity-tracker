package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val E7 = 1e7
private const val METERS_PER_DEGREE = 111_320.0
private const val OUTLINE_POINTS = 48

/**
 * A site's radius as maps draw it: a closed precision-5 polyline of [OUTLINE_POINTS] points [radius] meters around ([latE7], [lngE7]), in
 * 1e-7 degrees. Sites are small, so a flat offset per degree is exact enough.
 */
fun siteOutline(latE7: Int, lngE7: Int, radius: Int): String {
    val lat = latE7 / E7
    val lng = lngE7 / E7
    val dLat = radius / METERS_PER_DEGREE
    val dLng = radius / (METERS_PER_DEGREE * cos(lat * PI / 180.0))
    val points =
        (0..OUTLINE_POINTS).map { i ->
            val angle = 2 * PI * i / OUTLINE_POINTS
            GeoPoint(lat + dLat * cos(angle), lng + dLng * sin(angle))
        }
    return SegmentGeometry.toPrecision5(points)
}
