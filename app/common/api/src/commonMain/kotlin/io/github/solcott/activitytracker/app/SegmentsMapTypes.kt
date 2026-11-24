package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.location.toE7

/**
 * One segment's line on a map of many (segments/segment-browse): [uri] names it in selections, [polyline] is its line at precision 5, a
 * [lift] is drawn muted and can't be selected, and a run is drawn in its [rating]'s colour (segments/trail-ratings; null unrated). A line
 * that isn't a [trail] (a dive site's outline) is never rated and keeps the accent colour.
 */
@Immutable
data class MapSegmentLine(
    val uri: String,
    val polyline: String,
    val lift: Boolean = false,
    val rating: TrailRating? = null,
    val trail: Boolean = true,
    /** Riders' newest report says it's closed (segments/trail-reports): the map marks it "Closed", in words as well as colour. */
    val closed: Boolean = false,
)

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
        const val MAX_SPAN_E7 = SearchSegments.MAX_SPAN_E7

        /** What a map shows, from its box and centre in degrees, as every map reports it. */
        @Suppress("LongParameterList") // A box and a centre, as a map reports them.
        fun ofDegrees(south: Double, west: Double, north: Double, east: Double, centerLat: Double, centerLng: Double): MapViewport =
            MapViewport(
                minLat = south.toE7(),
                minLng = west.toE7(),
                maxLat = north.toE7(),
                maxLng = east.toE7(),
                centerLat = centerLat.toE7(),
                centerLng = centerLng.toE7(),
            )
    }
}

/**
 * Where a map should move to, once per [id]: centred on ([lat], [lng]) degrees at [zoom], or, when [bounds] is set, framing that box
 * whatever the map's size (a selected segment's line; segments/segment-browse).
 */
@Immutable
data class MapCameraRequest(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val zoom: Double = DEFAULT_ZOOM,
    val bounds: MapBounds? = null,
) {
    /**
     * The camera this request asks for on a map [width] by [height], in the map's units (dp, points), as each map applies it, a box framed
     * in what [bottom] (a sheet over the map's lower part) leaves open.
     */
    fun frame(width: Double, height: Double, bottom: Double = 0.0): MapFrame =
        bounds?.let { MapFrame.fit(it, width, height, bottom = bottom) } ?: MapFrame(latitude = lat, longitude = lng, zoom = zoom)

    companion object {
        /** About 10 km across on a phone: a town and its trails. */
        const val DEFAULT_ZOOM = 12.0

        /** A request to frame [bounds]; its centre stands in for a map that can't measure itself. */
        fun fit(id: Int, bounds: MapBounds): MapCameraRequest =
            MapCameraRequest(id, lat = (bounds.south + bounds.north) / 2, lng = (bounds.west + bounds.east) / 2, bounds = bounds)
    }
}
