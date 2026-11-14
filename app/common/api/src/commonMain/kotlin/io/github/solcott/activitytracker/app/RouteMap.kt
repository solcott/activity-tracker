package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.RouteCurve
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One piece of a route's line on a map, as the positions to draw through. */
@Immutable data class MapLine(val points: ImmutableList<GeoPoint>)

/** A break in a route's line on a map: the last position before it, where recording stopped, and the first after it, where it resumed. */
@Immutable data class MapBreak(val stop: GeoPoint, val resume: GeoPoint)

/** The box a route lies in, in degrees. A box across the antimeridian has an [east] past 180, so it's always east of [west]. */
@Immutable
data class MapBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    companion object {
        /**
         * The box around [points], taken the short way round: a route crossing the antimeridian (Fiji, Chukotka) spans a few degrees past
         * 180, not the whole world the other way.
         */
        fun of(points: List<GeoPoint>): MapBounds {
            val west = points.minOf { it.lng }
            val east = points.maxOf { it.lng }
            val south = points.minOf { it.lat }
            val north = points.maxOf { it.lat }
            if (east - west <= HALF_TURN) return MapBounds(west, south, east, north)
            // Measured eastwards from 0, the western hemisphere's longitudes come after 180.
            val shifted = points.map { if (it.lng < 0) it.lng + FULL_TURN else it.lng }
            val wrappedWest = shifted.min()
            val wrappedEast = shifted.max()
            return if (wrappedEast - wrappedWest < east - west) MapBounds(wrappedWest, south, wrappedEast, north)
            else MapBounds(west, south, east, north)
        }

        private const val FULL_TURN = 360.0
        private const val HALF_TURN = 180.0
    }
}

/**
 * A north-up camera: the map's centre at [latitude], [longitude] and its [zoom], where zoom 0 draws the Web Mercator world [WORLD] units
 * wide and each step doubles it (MapLibre's zoom, which the native map converts to its own).
 */
@Immutable
data class MapFrame(val latitude: Double, val longitude: Double, val zoom: Double) {
    companion object {
        /** The world's width in a map's units (points, dp) at zoom 0. */
        const val WORLD = 512.0

        /** The margin left around a framed route, in a map's units. */
        const val MARGIN = 24.0

        /** The closest a framed route is drawn: a few metres of route doesn't fill a card with one building. */
        const val MAX_ZOOM = 16.0

        /**
         * The camera that frames [bounds] in a map [width] by [height] with [margin] on every side and [top] more along the top (anything a
         * caller draws over the map's top edge): the zoom fits the tighter of the two directions in Web Mercator, clamped to [0, MAX_ZOOM],
         * and the centre is the middle of what's left.
         */
        fun fit(bounds: MapBounds, width: Double, height: Double, margin: Double = MARGIN, top: Double = 0.0): MapFrame {
            val west = mercatorX(bounds.west)
            val east = mercatorX(bounds.east)
            val north = mercatorY(bounds.north)
            val south = mercatorY(bounds.south)
            val room = (width - margin * 2).coerceAtLeast(1.0)
            val tall = (height - margin * 2 - top).coerceAtLeast(1.0)
            // A span of 0 (a single point, or a due north-south or east-west line) fits at any zoom in that direction.
            val zoomX = if (east > west) log2(room / ((east - west) * WORLD)) else Double.POSITIVE_INFINITY
            val zoomY = if (south > north) log2(tall / ((south - north) * WORLD)) else Double.POSITIVE_INFINITY
            val zoom = min(zoomX, zoomY).coerceIn(0.0, MAX_ZOOM)
            // The route sits in the middle of the room below [top], so the map's centre is half of [top] above the route's.
            val shift = top / 2 / (WORLD * pow2(zoom))
            val centreY = (north + south) / 2 - shift
            // A box across the antimeridian centres past 180; brought back into -180 until 180.
            val longitude = ((west + east) / 2 * FULL_TURN).mod(FULL_TURN) - HALF_TURN
            return MapFrame(latitude = latitude(centreY), longitude = longitude, zoom = zoom)
        }

        private fun pow2(zoom: Double): Double = exp(zoom * ln(2.0))

        /** [longitude] as a fraction of the world's width, from the antimeridian eastwards. */
        private fun mercatorX(longitude: Double): Double = (longitude + HALF_TURN) / FULL_TURN

        /** [latitude] as a fraction of the Web Mercator world's height, from the top. */
        private fun mercatorY(latitude: Double): Double {
            val phi = latitude * PI / HALF_TURN
            return (1 - ln(tan((PI / 2 + phi) / 2)) / PI) / 2
        }

        /** The latitude at [y], a fraction of the world's height from the top: [mercatorY]'s inverse. */
        private fun latitude(y: Double): Double = (2 * atan(exp(PI * (1 - 2 * y))) - PI / 2) * HALF_TURN / PI

        private const val FULL_TURN = 360.0
        private const val HALF_TURN = 180.0
    }
}

/**
 * A served route as every route map draws it (feed/route-markers, feed/map-breaks, feed/route-line-detail, dev/shared-display-text): the
 * [pieces] of its line between its breaks, each smoothed into curves; its [start] and [end] markers (at the ends others see when the map is
 * given them, none where they're hidden) and its [breaks] at the stored positions; a [highlight]ed stretch over it, such as a new
 * segment's, smoothed the same way; and its [bounds], from the stored positions too. The shared map, its line preview and the feed's map
 * images ask it, and so does the native iOS map, which lays it out on MapKit.
 */
@Immutable
@Suppress("LongParameterList") // Everything a route map draws, built only by [of].
class RouteMap
private constructor(
    private val polyline: String,
    private val gaps: ImmutableList<Int>?,
    private val highlighted: String?,
    val pieces: ImmutableList<MapLine>,
    val start: GeoPoint?,
    val end: GeoPoint?,
    val breaks: ImmutableList<MapBreak>,
    val bounds: MapBounds,
    /** A stretch drawn over the route, wider and in the highlight colour; null for none. */
    val highlight: MapLine?,
) {
    /**
     * The route and its marks as text: a UI that holds a map frames it again when this changes, and not on every new state. The [highlight]
     * isn't in it, so moving a stretch along the route leaves the camera where it is.
     */
    val key: String
        get() = "$polyline|$gaps|$start|$end"

    /** The camera that frames the route in a map [width] by [height], with [top] kept clear along the top. */
    fun frame(width: Double, height: Double, top: Double = 0.0): MapFrame = MapFrame.fit(bounds, width, height, top = top)

    /** The route as the line preview draws it on a tile [width] by [height] with [pad] kept clear, with this map's markers. */
    fun sketch(width: Double, height: Double, pad: Double): RouteShape? =
        RouteShape.of(polyline, gaps, width, height, pad)?.let { shape ->
            val fit = TileFit.of(allPoints(), width, height, pad) ?: return@let shape
            shape.copy(
                start = start?.let(fit::place),
                end = end?.let(fit::place),
                highlight = highlight?.let { line -> RoutePiece(line.points.map(fit::place).toImmutableList()) },
            )
        }

    private fun allPoints(): List<GeoPoint> = TrackMath.decodePolylineOrNull(polyline).orEmpty()

    override fun equals(other: Any?): Boolean =
        other is RouteMap &&
            polyline == other.polyline &&
            gaps == other.gaps &&
            start == other.start &&
            end == other.end &&
            highlighted == other.highlighted

    override fun hashCode(): Int = listOf(polyline, gaps, start, end, highlighted).hashCode()

    companion object {
        /**
         * [polyline] (an encoded polyline, as served) broken at [gaps] (`feed.activity#polylineGaps`), its start and end marked at [ends]
         * when given (the owner's untrimmed track marks where others' routes start and end; [RouteEnds.HIDDEN] marks none), with
         * [highlight] (an encoded polyline of the same precision) drawn over it when it decodes to two points or more; null when the route
         * decodes to fewer than two points.
         */
        fun of(polyline: String, gaps: List<Int>? = null, ends: RouteEnds? = null, highlight: String? = null): RouteMap? {
            val points = TrackMath.decodePolylineOrNull(polyline).orEmpty()
            if (points.size < 2) return null
            val drawn = SegmentedPolyline.pieces(points, gaps).filter { it.isNotEmpty() }
            val stretch = highlight?.let(TrackMath::decodePolylineOrNull)?.takeIf { it.size >= 2 }
            return RouteMap(
                polyline = polyline,
                gaps = gaps?.toImmutableList(),
                highlighted = highlight.takeIf { stretch != null },
                pieces = drawn.map { MapLine(RouteCurve.smooth(it).toImmutableList()) }.toImmutableList(),
                start = if (ends == null) drawn.first().first() else ends.start,
                end = if (ends == null) drawn.last().last() else ends.end,
                breaks = drawn.zipWithNext { before, after -> MapBreak(before.last(), after.first()) }.toImmutableList(),
                bounds = MapBounds.of(points),
                highlight = stretch?.let { MapLine(RouteCurve.smooth(it).toImmutableList()) },
            )
        }
    }
}
