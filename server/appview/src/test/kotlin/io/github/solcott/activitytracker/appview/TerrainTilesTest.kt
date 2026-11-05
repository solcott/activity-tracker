package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.TerrainTiles
import io.github.solcott.activitytracker.location.GeoPoint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(0L, tiles.elevationOf(listOf(GeoPoint(45.0, -90.0), GeoPoint(45.01, -90.0)))?.gain)
        assertNull(tiles.elevationOf(listOf(GeoPoint(45.0, -0.0001), GeoPoint(45.0, 0.0001))), "crosses into the missing tile")
    }
}
