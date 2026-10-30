package io.github.solcott.activitytracker.app.ui

import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

// Skia, which Compose ships on iOS, desktop and web, decodes and encodes WebP alike; iOS's own ImageIO can't write WebP.
internal actual fun encodePhoto(image: ByteArray, maxPixels: Int, quality: Int): CompressedPhoto {
    val codec = Codec.makeFromData(Data.makeFromBytes(image))
    val origin = codec.encodedOrigin
    codec.close()
    val source = Image.makeFromEncoded(image)
    val turned = origin in TURNED
    // The size it shows at, once turned upright.
    val shownWidth = if (turned) source.height else source.width
    val shownHeight = if (turned) source.width else source.height
    val scale = minOf(1f, maxPixels.toFloat() / max(shownWidth, shownHeight))
    val width = (shownWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (shownHeight * scale).roundToInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.apply {
        scale(scale, scale)
        concat(upright(origin, source.width.toFloat(), source.height.toFloat()))
        drawImageRect(source, Rect.makeWH(source.width.toFloat(), source.height.toFloat()))
    }
    val snapshot = surface.makeImageSnapshot()
    snapshot.encodeToData(EncodedImageFormat.WEBP, quality)?.let {
        return CompressedPhoto(it.bytes, WEBP, width, height)
    }
    val jpeg = snapshot.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY) ?: error("Couldn't encode the photo")
    return CompressedPhoto(jpeg.bytes, JPEG, width, height)
}

private val TURNED = setOf(EncodedOrigin.LEFT_TOP, EncodedOrigin.RIGHT_TOP, EncodedOrigin.RIGHT_BOTTOM, EncodedOrigin.LEFT_BOTTOM)

/** Maps the stored [w]×[h] pixels to the upright photo, as the camera's EXIF [origin] says it was held. */
private fun upright(origin: EncodedOrigin, w: Float, h: Float): Matrix33 =
    when (origin) {
        EncodedOrigin.TOP_RIGHT -> Matrix33(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f)
        EncodedOrigin.BOTTOM_RIGHT -> Matrix33(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f)
        EncodedOrigin.BOTTOM_LEFT -> Matrix33(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f)
        EncodedOrigin.LEFT_TOP -> Matrix33(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        EncodedOrigin.RIGHT_TOP -> Matrix33(0f, -1f, h, 1f, 0f, 0f, 0f, 0f, 1f)
        EncodedOrigin.RIGHT_BOTTOM -> Matrix33(0f, -1f, h, -1f, 0f, w, 0f, 0f, 1f)
        EncodedOrigin.LEFT_BOTTOM -> Matrix33(0f, 1f, 0f, -1f, 0f, w, 0f, 0f, 1f)
        else -> Matrix33.IDENTITY
    }
