package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.location.ElevationGain
import io.github.solcott.activitytracker.location.GainLoss
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Terrain heights from Terrarium elevation tiles (segments/segment-elevation): 256-pixel Web Mercator PNGs at `[dir]/{z}/{x}/{y}.png`, all
 * at one [zoom], each pixel's height `R * 256 + G + B / 256 - 32768` meters. A tile that isn't there leaves its area uncovered. Recently
 * read tiles are kept decoded, [CACHED_TILES] at most.
 */
class TerrainTiles(private val dir: File, private val zoom: Int) {
    private val tiles = (1 shl zoom)
    private val cache =
        object : LinkedHashMap<Long, FloatArray?>(CACHED_TILES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FloatArray?>) = size > CACHED_TILES
        }

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
     * [line]'s gain and loss sampled every [SAMPLE_SPACING_M] meters, both ends included; null when any sample isn't covered, so a segment
     * is given terrain elevation only when the tiles cover all of it.
     */
    fun elevationOf(line: List<GeoPoint>): GainLoss? {
        val heights = SegmentGeometry.resample(line, SAMPLE_SPACING_M).map { heightAt(it) ?: return null }
        return ElevationGain.of(heights)
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

    @Synchronized
    private fun tile(tx: Int, ty: Int): FloatArray? {
        val key = tx.toLong() shl Int.SIZE_BITS or ty.toLong()
        if (cache.containsKey(key)) return cache[key]
        val heights = read(File(dir, "$zoom/$tx/$ty.png"))
        cache[key] = heights
        return heights
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
        /** Spacing of terrain samples along a segment's line, in meters. */
        const val SAMPLE_SPACING_M = 10.0

        /** How many decoded tiles are kept: about 16 MB. */
        const val CACHED_TILES = 64

        const val TILE_SIZE = 256
        private const val LOAD_FACTOR = 0.75f
        private const val HALF = 0.5
        private const val HALF_TURN_DEGREES = 180.0
        private const val FULL_TURN_DEGREES = 360.0
        private const val MAX_MERCATOR_LAT = 85.05112878
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BYTE = 0xFF
        private const val BYTE_VALUES = 256
        private const val TERRARIUM_OFFSET = 32768f
    }
}
