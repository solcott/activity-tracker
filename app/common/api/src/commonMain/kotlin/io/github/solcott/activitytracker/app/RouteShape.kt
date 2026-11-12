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

/**
 * Where each point goes on a tile [width] by [height] to fit all of [points] inside [pad]: scaled together, centred, north up, with
 * longitudes shrunk by the cosine of the first point's latitude so shapes keep their proportions. The route and segment stand-ins on every
 * platform place their lines with it ([RouteShape], the shared UI's `canvasFit`).
 */
class TileFit private constructor(points: List<GeoPoint>, width: Double, height: Double, pad: Double) {
    private val latScale = cos(points.first().lat * PI / HALF_TURN)
    private val minX = points.minOf { it.lng * latScale }
    private val maxY = points.maxOf { it.lat }
    private val scale: Double
    private val offsetX: Double
    private val offsetY: Double

    init {
        val spanX = (points.maxOf { it.lng * latScale } - minX).coerceAtLeast(MIN_SPAN)
        val spanY = (maxY - points.minOf { it.lat }).coerceAtLeast(MIN_SPAN)
        scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        offsetX = (width - spanX * scale) / 2
        offsetY = (height - spanY * scale) / 2
    }

    /** Where [point] goes on the tile. */
    fun place(point: GeoPoint): ShapePoint =
        ShapePoint((point.lng * latScale - minX) * scale + offsetX, (maxY - point.lat) * scale + offsetY)

    companion object {
        /** The fit of [points] to the tile; null for no points. */
        fun of(points: List<GeoPoint>, width: Double, height: Double, pad: Double): TileFit? =
            if (points.isEmpty()) null else TileFit(points, width, height, pad)

        private const val HALF_TURN = 180.0
        private const val MIN_SPAN = 1e-9
    }
}

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
    /** Where the start marker goes; null where the map shows none ([RouteMap.sketch] with hidden ends). */
    val start: ShapePoint?,
    /** Where the end marker goes; null where the map shows none. */
    val end: ShapePoint?,
    val breaks: ImmutableList<RouteBreak>,
) {
    companion object {
        /**
         * [polyline] (an encoded polyline, as served) broken at [gaps] (`feed.activity#polylineGaps`), on a tile [width] by [height] with
         * [pad] kept clear on every side; null when it decodes to fewer than two points.
         */
        fun of(polyline: String, gaps: List<Int>?, width: Double, height: Double, pad: Double): RouteShape? {
            val points = TrackMath.decodePolylineOrNull(polyline).orEmpty()
            val fit = points.takeIf { it.size >= 2 }?.let { TileFit.of(it, width, height, pad) } ?: return null
            val pieces = SegmentedPolyline.pieces(points, gaps).filter { it.isNotEmpty() }
            return RouteShape(
                pieces = pieces.map { piece -> RoutePiece(RouteCurve.smooth(piece).map(fit::place).toImmutableList()) }.toImmutableList(),
                start = fit.place(pieces.first().first()),
                end = fit.place(pieces.last().last()),
                breaks =
                    pieces
                        .zipWithNext { before, after -> RouteBreak(fit.place(before.last()), fit.place(after.first())) }
                        .toImmutableList(),
            )
        }
    }
}
