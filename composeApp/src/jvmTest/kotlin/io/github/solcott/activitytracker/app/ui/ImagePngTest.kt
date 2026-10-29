package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class ImagePngTest {
    @Test
    fun roundTripsSizeAndPixels() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.makeRGB(12, 34, 56))
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32(3, 2, ColorAlphaType.OPAQUE)) }
        colors.forEachIndexed { i, color -> bitmap.erase(color, org.jetbrains.skia.IRect.makeXYWH(i % 3, i / 3, 1, 1)) }

        val decoded = Image.makeFromEncoded(bitmap.asComposeImageBitmap().encodePng())

        assertEquals(3 to 2, decoded.width to decoded.height)
        val pixels = Bitmap.makeFromImage(decoded)
        assertContentEquals(colors, IntArray(6) { pixels.getColor(it % 3, it / 3) })
    }
}
