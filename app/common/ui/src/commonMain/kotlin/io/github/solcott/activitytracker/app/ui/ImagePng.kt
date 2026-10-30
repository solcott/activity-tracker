package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.graphics.ImageBitmap

/** This image as a PNG: lossless, so a map's lines and labels stay crisp. */
internal expect fun ImageBitmap.encodePng(): ByteArray
