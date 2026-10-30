package io.github.solcott.activitytracker.app.profile

import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

// FileKit has no image compression on the web, so scale and encode with Skia, which Compose already ships there.
actual suspend fun compressAvatar(image: ByteArray, maxPixels: Int, quality: Int): ByteArray {
    val source = Image.makeFromEncoded(image)
    val scale = minOf(1f, maxPixels.toFloat() / max(source.width, source.height))
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(source, Rect.makeWH(width.toFloat(), height.toFloat()))
    val jpeg = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality) ?: error("Couldn't encode the picture as JPEG")
    return jpeg.bytes
}
