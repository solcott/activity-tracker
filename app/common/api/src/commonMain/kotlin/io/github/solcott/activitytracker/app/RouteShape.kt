package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.RouteCurve
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A point placed on a tile, in the tile's own units from its top-leading corner. */
@Immutable data class ShapePoint(val x: Double, val y: Double)

/** One piece of a route's line, between two of its breaks, as the points to draw through. */
@Immutable data class RoutePiece(val points: ImmutableList<ShapePoint>)

/** A break in the line: the last point before it, where recording stopped, and the first after it, where it resumed. */
@Immutable data class RouteBreak(val stop: ShapePoint, val resume: ShapePoint)

/**
 * A served route placed on a tile, for a UI to draw as the line preview (feed/feed-route-map, dev/ios-swift-interop): each piece of its
 * line smoothed into curves (feed/route-line-detail), and its start, end and breaks at the stored points (feed/route-markers,
 * feed/map-breaks), all fitted north-up into the tile inside a margin, with longitudes shrunk by the first point's latitude so the shape
 * keeps its proportions. The shared line preview fits a route with the same rule (`canvasFit`).
 */
@Immutable
data class RouteShape(
    val pieces: ImmutableList<RoutePiece>,
    val start: ShapePoint,
    val end: ShapePoint,
    val breaks: ImmutableList<RouteBreak>,
) {
    companion object {
        /**
         * [polyline] (an encoded polyline, as served) broken at [gaps] (`feed.activity#polylineGaps`), on a tile [width] by [height] with
         * [pad] kept clear on every side; null when it decodes to fewer than two points.
         */
        fun of(polyline: String, gaps: List<Int>?, width: Double, height: Double, pad: Double): RouteShape? {
            // As points of our own, since this module's signatures name no pair (dev/ios-swift-interop).
            val points = TrackMath.decodePolylineOrNull(polyline).orEmpty().map { (lat, lng) -> GeoPoint(lat, lng) }
            if (points.size < 2) return null
            val place = fit(points, width, height, pad)
            val pieces = SegmentedPolyline.pieces(points, gaps).filter { it.isNotEmpty() }
            return RouteShape(
                pieces =
                    pieces
                        .map { piece ->
                            val smooth = RouteCurve.smooth(piece.map { it.lat to it.lng })
                            RoutePiece(smooth.map { (lat, lng) -> place(GeoPoint(lat, lng)) }.toImmutableList())
                        }
                        .toImmutableList(),
                start = place(pieces.first().first()),
                end = place(pieces.last().last()),
                breaks = pieces.zipWithNext { before, after -> RouteBreak(place(before.last()), place(after.first())) }.toImmutableList(),
            )
        }

        private fun fit(points: List<GeoPoint>, width: Double, height: Double, pad: Double): (GeoPoint) -> ShapePoint {
            val latScale = cos(points.first().lat * PI / HALF_TURN)
            val minX = points.minOf { it.lng * latScale }
            val maxY = points.maxOf { it.lat }
            val spanX = (points.maxOf { it.lng * latScale } - minX).coerceAtLeast(MIN_SPAN)
            val spanY = (maxY - points.minOf { it.lat }).coerceAtLeast(MIN_SPAN)
            val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
            val offsetX = (width - spanX * scale) / 2
            val offsetY = (height - spanY * scale) / 2
            return { (lat, lng) -> ShapePoint((lng * latScale - minX) * scale + offsetX, (maxY - lat) * scale + offsetY) }
        }

        private const val HALF_TURN = 180.0
        private const val MIN_SPAN = 1e-9
    }
}
