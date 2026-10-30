package io.github.solcott.activitytracker.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

internal actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().also { asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, LOSSLESS, it) }.toByteArray()

/** PNG ignores the quality, but `compress` asks for one. */
private const val LOSSLESS = 100
