package io.github.solcott.activitytracker.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal actual fun encodePhoto(image: ByteArray, maxPixels: Int, quality: Int): CompressedPhoto {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
    // Decode at no more than twice the size we keep, so a 50 MP photo doesn't take hundreds of megabytes.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixels * 2) sample *= 2
    val decoded =
        BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Couldn't decode the photo")
    val scale = minOf(1f, maxPixels.toFloat() / max(decoded.width, decoded.height))
    val matrix = uprightMatrix(image).apply { postScale(scale, scale) }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val out = ByteArrayOutputStream()
    @Suppress("DEPRECATION") // WEBP_LOSSY needs API 30; before it, WEBP below quality 100 is lossy.
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    upright.compress(format, quality.coerceAtMost(MAX_LOSSY_QUALITY), out)
    return CompressedPhoto(out.toByteArray(), WEBP, upright.width, upright.height).also {
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }
}

/** Turns the photo the way its camera recorded it was held. */
private fun uprightMatrix(image: ByteArray): Matrix =
    Matrix().apply {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(image)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(HALF_TURN)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(THREE_QUARTER_TURN)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(QUARTER_TURN)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(THREE_QUARTER_TURN)
                postScale(-1f, 1f)
            }
        }
    }

private const val MAX_LOSSY_QUALITY = 99
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val THREE_QUARTER_TURN = 270f
