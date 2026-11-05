package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable

/**
 * One segment's line on a map of many (segments/segment-browse): [uri] names it in selections, [polyline] is its line at precision 5, and a
 * [lift] is drawn muted and can't be selected.
 */
@Immutable data class MapSegmentLine(val uri: String, val polyline: String, val lift: Boolean = false)

/**
 * What a map shows, in 1e-7 degrees: the box it covers and its centre. [tooWide] when the box is wider than one degree on either side,
 * further out than segments are searched.
 */
@Immutable
data class MapViewport(
    val minLat: Int,
    val minLng: Int,
    val maxLat: Int,
    val maxLng: Int,
    val centerLat: Int,
    val centerLng: Int,
) {
    val tooWide: Boolean
        get() = maxLat - minLat > MAX_SPAN_E7 || maxLng - minLng > MAX_SPAN_E7

    companion object {
        /** One degree, as `feed.searchSegments` limits a box. */
        const val MAX_SPAN_E7 = 10_000_000
    }
}

/** Where a map should move to, once per [id]: centred on ([lat], [lng]) degrees at [zoom]. */
@Immutable
data class MapCameraRequest(val id: Int, val lat: Double, val lng: Double, val zoom: Double = DEFAULT_ZOOM) {
    companion object {
        /** About 10 km across on a phone: a town and its trails. */
        const val DEFAULT_ZOOM = 12.0
    }
}
