package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.boundedCache
import io.github.solcott.activitytracker.appview.index.E7
import io.github.solcott.activitytracker.location.ElevationGain
import io.github.solcott.activitytracker.location.ElevationProfile
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Terrain heights from Terrarium elevation tiles (segments/segment-elevation): 256-pixel Web Mercator PNGs at `[dir]/{z}/{x}/{y}.png`, all
 * at one [zoom], each pixel's height `R * 256 + G + B / 256 - 32768` meters. A tile that isn't there leaves its area uncovered. Recently
 * read tiles are kept decoded, [CACHED_TILES] at most, and a missing one is remembered as missing. Which tiles are there is listed once,
 * the first time [coverage] is read, so tiles added later count from the next start.
 */
class TerrainTiles(private val dir: File, private val zoom: Int) {
    private val tiles = (1 shl zoom)
    /** Decoded tiles by [key]; [MISSING] for one that isn't there or doesn't decode, as Caffeine holds no nulls. */
    private val cache = boundedCache<Long, FloatArray>(CACHED_TILES.toLong())

    /**
     * The areas the tiles at [zoom] cover, from the `{x}/{y}.png` files present: each column's runs of consecutive tiles, merged with the
     * next column's when it has the same run. A box that touches none of them has no terrain, so it needn't be sampled.
     */
    val coverage: List<TerrainArea> by lazy { areasOf(columns()) }

    /** Whether [coverage] touches the box from ([minLatE7], [minLngE7]) to ([maxLatE7], [maxLngE7]), in degrees times 10^7. */
    fun touches(minLatE7: Int, maxLatE7: Int, minLngE7: Int, maxLngE7: Int): Boolean = coverage.any {
        maxLatE7 >= it.minLatE7 && minLatE7 <= it.maxLatE7 && maxLngE7 >= it.minLngE7 && minLngE7 <= it.maxLngE7
    }

    /** Each column of tiles present, by x, as its sorted runs of consecutive y. */
    private fun columns(): Map<Int, List<IntRange>> =
        File(dir, "$zoom")
            .listFiles()
            .orEmpty()
            .mapNotNull { column ->
                val x = column.name.toIntOrNull()?.takeIf { column.isDirectory } ?: return@mapNotNull null
                val ys = column.list().orEmpty().mapNotNull { it.removeSuffix(".png").takeIf { y -> y != it }?.toIntOrNull() }.sorted()
                x to runsOf(ys)
            }
            .filter { it.second.isNotEmpty() }
            .toMap()

    /** [columns] merged into rectangles of tiles and turned into degrees, rounded outwards. */
    private fun areasOf(columns: Map<Int, List<IntRange>>): List<TerrainArea> {
        // Rectangles still growing eastwards, by their run of y, as the first x they span.
        var open = mapOf<IntRange, Int>()
        val areas = mutableListOf<TerrainArea>()
        fun close(ys: IntRange, firstX: Int, lastX: Int) {
            areas +=
                TerrainArea(
                    minLatE7 = floor(latOf(ys.last + 1) * E7).toInt(),
                    maxLatE7 = ceil(latOf(ys.first) * E7).toInt(),
                    minLngE7 = floor(lngOf(firstX) * E7).toInt(),
                    maxLngE7 = ceil(lngOf(lastX + 1) * E7).toInt(),
                )
        }
        // Tile x is never negative, so the first column is never adjacent; open is empty until it's set.
        var previousX = Int.MIN_VALUE
        for ((x, runs) in columns.toSortedMap()) {
            val adjacent = previousX == x - 1
            val next = runs.associateWith { ys -> open[ys]?.takeIf { adjacent } ?: x }
            for ((ys, firstX) in open) if (next[ys] != firstX) close(ys, firstX, previousX)
            open = next
            previousX = x
        }
        for ((ys, firstX) in open) close(ys, firstX, previousX)
        return areas
    }

    private fun lngOf(tileX: Int): Double = tileX.toDouble() / tiles * FULL_TURN_DEGREES - HALF_TURN_DEGREES

    private fun latOf(tileY: Int): Double = atan(sinh(PI * (1 - 2.0 * tileY / tiles))) * HALF_TURN_DEGREES / PI

    /** The height at [point] in meters, interpolated between the four nearest pixels; null when a tile it needs isn't there. */
    fun heightAt(point: GeoPoint): Double? {
        // Global pixel coordinates, offset so integer values are pixel centres.
        val x = (point.lng + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * tiles * TILE_SIZE - HALF
        val latRad = point.lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT) * PI / HALF_TURN_DEGREES
        val y = (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * tiles * TILE_SIZE - HALF
        val x0 = floor(x).toLong()
        val y0 = floor(y).toLong()
        val fx = x - x0
        val fy = y - y0
        // Interpolated along each of the two nearest rows of pixels, then between them; none when any of the four isn't covered.
        val top = lerp(pixel(x0, y0), pixel(x0 + 1, y0), fx)
        val bottom = lerp(pixel(x0, y0 + 1), pixel(x0 + 1, y0 + 1), fx)
        return lerp(top, bottom, fy)
    }

    /**
     * [line]'s terrain (segments/segment-elevation): heights sampled every [SAMPLE_SPACING_M] meters, both ends included, smoothed over 50
     * m ([ElevationProfile.smooth]), then counted into gain and loss, resampled into an evenly spaced profile, and measured for its
     * steepest grade ([ElevationProfile.maxGrade]) at full sample resolution. Null when any sample isn't covered, so a segment is given
     * terrain elevation only when the tiles cover all of it.
     */
    fun terrainOf(line: List<GeoPoint>): TerrainElevation? {
        val samples = SegmentGeometry.resample(line, SAMPLE_SPACING_M)
        val raw = samples.map { heightAt(it) ?: return null }
        val heights = ElevationProfile.smooth(raw, SAMPLE_SPACING_M)
        val atM = samples.zipWithNext(SegmentGeometry::distanceMeters).runningFold(0.0, Double::plus)
        val gainLoss = ElevationGain.of(heights)
        val maxGrade = ElevationProfile.maxGrade(heights, atM)
        return if (gainLoss == null || maxGrade == null) {
            null
        } else {
            TerrainElevation(gainLoss.gain, gainLoss.loss, ElevationProfile.even(heights, atM), (maxGrade * TENTHS).roundToInt())
        }
    }

    /** [f] of the way from [a] to [b]; null when either is. */
    private fun lerp(a: Double?, b: Double?, f: Double): Double? = if (a == null || b == null) null else a + (b - a) * f

    /** The height of global pixel ([px], [py]), wrapping round the antimeridian; null when its tile isn't there or doesn't decode. */
    private fun pixel(px: Long, py: Long): Double? {
        val size = tiles.toLong() * TILE_SIZE
        val gx = Math.floorMod(px, size)
        val gy = py.coerceIn(0, size - 1)
        val heights = tile((gx / TILE_SIZE).toInt(), (gy / TILE_SIZE).toInt()) ?: return null
        return heights[(gy % TILE_SIZE).toInt() * TILE_SIZE + (gx % TILE_SIZE).toInt()].toDouble()
    }

    private fun tile(tx: Int, ty: Int): FloatArray? {
        val key = tx.toLong() shl Int.SIZE_BITS or ty.toLong()
        return cache.get(key) { read(File(dir, "$zoom/$tx/$ty.png")) ?: MISSING }.takeIf { it !== MISSING }
    }

    /** [file]'s heights, row by row; null when it isn't there, doesn't decode or isn't a whole tile. */
    private fun read(file: File): FloatArray? {
        val image =
            try {
                file.takeIf { it.isFile }?.let(ImageIO::read)
            } catch (_: IOException) {
                null
            }
        if (image == null || image.width != TILE_SIZE || image.height != TILE_SIZE) return null
        return FloatArray(TILE_SIZE * TILE_SIZE) { i ->
            val rgb = image.getRGB(i % TILE_SIZE, i / TILE_SIZE)
            val r = rgb shr RED_SHIFT and BYTE
            val g = rgb shr GREEN_SHIFT and BYTE
            val b = rgb and BYTE
            (r * BYTE_VALUES + g + b / BYTE_VALUES.toFloat() - TERRARIUM_OFFSET)
        }
    }

    companion object {
        /** The runs of consecutive values in [sorted]. */
        private fun runsOf(sorted: List<Int>): List<IntRange> {
            val runs = mutableListOf<IntRange>()
            for (y in sorted) {
                val last = runs.lastOrNull()
                if (last != null && y == last.last + 1) runs[runs.lastIndex] = last.first..y else if (last?.last != y) runs += y..y
            }
            return runs
        }

        /** Spacing of terrain samples along a segment's line, in meters. */
        const val SAMPLE_SPACING_M = 10.0

        /** How many decoded tiles are kept: about 16 MB. */
        const val CACHED_TILES = 64

        const val TILE_SIZE = 256
        private val MISSING = FloatArray(0)
        private const val HALF = 0.5
        private const val HALF_TURN_DEGREES = 180.0
        private const val FULL_TURN_DEGREES = 360.0
        private const val MAX_MERCATOR_LAT = 85.05112878
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BYTE = 0xFF
        private const val BYTE_VALUES = 256
        private const val TERRARIUM_OFFSET = 32768f
        private const val TENTHS = 10.0
    }
}

/**
 * A segment's terrain: gain and loss in whole meters, [profileDm], its smoothed heights evenly spaced from start to end, in decimeters, and
 * [maxGradeTenths], its steepest absolute grade over about 50 m, in tenths of a percent.
 */
data class TerrainElevation(val gain: Long, val loss: Long, val profileDm: List<Int>, val maxGradeTenths: Int)

/** An area terrain tiles cover, in degrees times 10^7, edges included. */
data class TerrainArea(val minLatE7: Int, val maxLatE7: Int, val minLngE7: Int, val maxLngE7: Int)
