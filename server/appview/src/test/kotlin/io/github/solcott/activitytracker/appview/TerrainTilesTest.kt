package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.TerrainTiles
import io.github.solcott.activitytracker.location.ElevationGain
import io.github.solcott.activitytracker.location.ElevationProfile
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sinh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Terrarium tile decoding and lookup (segments/segment-elevation). */
class TerrainTilesTest {
    /** A zoom-1 tile set with only the western hemisphere's northern tile, every pixel [height] meters. */
    private fun tiles(height: Double): TerrainTiles {
        val dir = createTempDirectory("terrain").toFile().apply { deleteOnExit() }
        val value = height + 32768
        val rgb = ((value / 256).toInt() shl 16) or ((value.toInt() % 256) shl 8) or ((value - value.toInt()) * 256).toInt()
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 256) for (y in 0 until 256) image.setRGB(x, y, rgb)
        File(dir, "1/0").mkdirs()
        ImageIO.write(image, "png", File(dir, "1/0/0.png"))
        return TerrainTiles(dir, 1)
    }

    @Test
    fun coverageMergesAdjacentColumnsAndKeepsApartTheRest() {
        val dir = createTempDirectory("terrain").toFile().apply { deleteOnExit() }
        // Zoom 2: the north-western quarter, two columns of two tiles each, and one tile in the south-east.
        for ((x, y) in listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 3 to 2)) {
            File(dir, "2/$x").mkdirs()
            File(dir, "2/$x/$y.png").writeBytes(ByteArray(0))
        }
        File(dir, "2/3/notes.txt").writeText("ignored")
        val tiles = TerrainTiles(dir, 2)

        val areas = tiles.coverage.sortedBy { it.minLngE7 }
        assertEquals(2, areas.size, "$areas")
        val (west, east) = areas
        assertEquals(-1_800_000_000, west.minLngE7)
        assertEquals(0, west.maxLngE7)
        assertEquals(0, west.minLatE7)
        assertTrue(abs(west.maxLatE7 - 850_511_288) <= 1, "$west")
        assertEquals(900_000_000, east.minLngE7)
        assertEquals(1_800_000_000, east.maxLngE7)
        assertTrue(abs(east.minLatE7 + 665_132_604) <= 1, "$east")
        assertEquals(0, east.maxLatE7)

        assertTrue(tiles.touches(400_000_000, 410_000_000, -1_000_000_000, -990_000_000))
        assertTrue(tiles.touches(-300_000_000, -290_000_000, 1_000_000_000, 1_010_000_000))
        // The south-west and north-east have no tiles.
        assertTrue(!tiles.touches(-300_000_000, -290_000_000, -1_000_000_000, -990_000_000))
        assertTrue(!tiles.touches(400_000_000, 410_000_000, 1_000_000_000, 1_010_000_000))
    }

    @Test
    fun noTilesCoverNothing() {
        val dir = createTempDirectory("terrain").toFile().apply { deleteOnExit() }
        assertTrue(TerrainTiles(dir, 12).coverage.isEmpty())
    }

    @Test
    fun decodesTerrariumHeights() {
        assertEquals(1234.5, tiles(1234.5).heightAt(GeoPoint(45.0, -90.0)))
        assertEquals(-50.25, tiles(-50.25).heightAt(GeoPoint(45.0, -90.0)))
    }

    @Test
    fun aMissingTileIsUncovered() {
        val tiles = tiles(100.0)
        assertNull(tiles.heightAt(GeoPoint(45.0, 90.0)), "the eastern tile isn't there")
        assertNull(tiles.heightAt(GeoPoint(-45.0, -90.0)), "nor the southern")
    }

    @Test
    fun aLineIsCoveredOnlyWhollyInside() {
        val tiles = tiles(100.0)
        val flat = tiles.terrainOf(listOf(GeoPoint(45.0, -90.0), GeoPoint(45.01, -90.0)))
        assertEquals(0L, flat?.gain)
        assertEquals(ElevationProfile.PROFILE_POINTS, flat?.profileDm?.size)
        assertTrue(flat!!.profileDm.all { it == 1000 }, "a flat 100 m in decimeters")
        assertNull(tiles.terrainOf(listOf(GeoPoint(45.0, -0.0001), GeoPoint(45.0, 0.0001))), "crosses into the missing tile")
    }

    @Test
    fun aMissingTileStaysMissing() {
        val tiles = tiles(100.0)
        repeat(3) { assertNull(tiles.heightAt(GeoPoint(45.0, 90.0))) }
        assertEquals(100.0, tiles.heightAt(GeoPoint(45.0, -90.0)))
    }

    @Test
    fun shortRipplesAreSmoothedAway() {
        // One zoom-16 tile near 45 degrees north whose terrain climbs 10% northwards and also steps up 6 m and back every 12 pixels, about
        // 20 m.
        val zoom = 16
        val n = 1 shl zoom
        val tx = n / 4
        val ty = (n * 0.36).toInt()
        val pixelM = 40_075_016.686 * cos(Math.toRadians(latOf(ty * 256 + 128.0, n))) / (n * 256)
        val dir = createTempDirectory("terrain").toFile().apply { deleteOnExit() }
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
        for (py in 0 until 256) {
            val height = 1000.0 + (255 - py) * pixelM * 0.1 + if ((py / 6) % 2 == 0) 6.0 else 0.0
            val value = height + 32768
            val rgb = ((value / 256).toInt() shl 16) or ((value.toInt() % 256) shl 8) or ((value - value.toInt()) * 256).toInt()
            for (px in 0 until 256) image.setRGB(px, py, rgb)
        }
        File(dir, "$zoom/$tx").mkdirs()
        ImageIO.write(image, "png", File(dir, "$zoom/$tx/$ty.png"))
        val tiles = TerrainTiles(dir, zoom)

        val lng = (tx + 0.5) / n * 360 - 180
        val line = listOf(GeoPoint(latOf(ty * 256 + 230.0, n), lng), GeoPoint(latOf(ty * 256 + 30.0, n), lng))
        val climb = 200 * pixelM * 0.1
        val raw = ElevationGain.of(SegmentGeometry.resample(line, TerrainTiles.SAMPLE_SPACING_M).map(tiles::heightAt))!!
        assertTrue(raw.loss > 0, "unsmoothed, the ripples count: $raw")

        val terrain = tiles.terrainOf(line)!!
        assertEquals(0L, terrain.loss)
        // The ends keep their own heights, which may sit on a step's top or bottom.
        assertTrue(abs(terrain.gain - climb) <= 7, "gain ${terrain.gain}, climb $climb")
    }

    /** The latitude of global pixel row [y] at a zoom of [n] tiles a side. */
    private fun latOf(y: Double, n: Int): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / (n * 256)))))
}
