package io.github.solcott.activitytracker.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

class PhotoCompressionTest {
    /** A noisy 4000×3000 PNG, like a phone camera's full-size photo. */
    private fun cameraPhoto(): ByteArray {
        val surface = Surface.makeRasterN32Premul(4000, 3000)
        val paint = Paint()
        var seed = 1
        for (x in 0 until 4000 step 20) for (y in 0 until 3000 step 20) {
            seed = seed * 1103515245 + 12345
            paint.color = Color.makeRGB(seed ushr 16 and 0xff, seed ushr 8 and 0xff, seed and 0xff)
            surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 20f, 20f), paint)
        }
        return requireNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
    }

    @Test
    fun scalesALargePhotoDownToWebpUnderTheTarget() = runTest {
        val photo = compressPhoto(cameraPhoto())

        assertEquals("image/webp", photo.mimeType)
        assertEquals(2048 to 1536, photo.width to photo.height)
        assertTrue(photo.bytes.size <= 1_500_000, "${photo.bytes.size} bytes")
        val decoded = Image.makeFromEncoded(photo.bytes)
        assertEquals(2048 to 1536, decoded.width to decoded.height)
    }

    @Test
    fun keepsASmallPhotosSize() {
        val small = requireNotNull(Surface.makeRasterN32Premul(300, 400).makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
        val photo = encodePhoto(small, maxPixels = 2048, quality = 80)
        assertEquals(300 to 400, photo.width to photo.height)
    }
}
