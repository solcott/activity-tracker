package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * The grid the AppView indexes segments and segment tracks by, to find which may meet: cells of 0.01° (about 1.1 km), each packed into one
 * `Long` as `latCell * 100_000 + lngCell`.
 */
object SegmentCells {
    const val CELLS_PER_DEGREE = 100

    /** How near a cell's edge a point must be for the neighbouring cell to count too, in meters. */
    const val MARGIN_M = 30.0

    private const val LAT_FACTOR = 100_000L
    private const val METERS_PER_DEGREE = 111_320.0
    private const val DEGREES_PER_HALF_TURN = 180.0

    fun cellOf(lat: Double, lng: Double): Long = pack(index(lat), index(lng))

    /** The cell of [point] and of each neighbour within [margin] meters of it. */
    fun cellsOf(point: GeoPoint, margin: Double = MARGIN_M): Set<Long> {
        val dLat = margin / METERS_PER_DEGREE
        val dLng = margin / (METERS_PER_DEGREE * cos(point.lat * PI / DEGREES_PER_HALF_TURN).coerceAtLeast(0.01))
        val lats = setOf(index(point.lat - dLat), index(point.lat), index(point.lat + dLat))
        val lngs = setOf(index(point.lng - dLng), index(point.lng), index(point.lng + dLng))
        return buildSet { for (la in lats) for (ln in lngs) add(pack(la, ln)) }
    }

    /** The cells [line] passes through, sampled every [SegmentGeometry.REFERENCE_SPACING_M], each with its neighbours within [margin]. */
    fun cellsOf(line: List<GeoPoint>, margin: Double = MARGIN_M): Set<Long> =
        SegmentGeometry.resample(line).flatMapTo(mutableSetOf()) { cellsOf(it, margin) }

    /** Every cell a box in degrees covers. */
    fun cellsIn(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<Long> = buildList {
        for (la in index(minLat)..index(maxLat)) for (ln in index(minLng)..index(maxLng)) add(pack(la, ln))
    }

    private fun index(degrees: Double): Long = floor(degrees * CELLS_PER_DEGREE).toLong()

    private fun pack(latCell: Long, lngCell: Long): Long = latCell * LAT_FACTOR + lngCell
}
