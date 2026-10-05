package io.github.solcott.activitytracker.app.profile

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage

actual suspend fun compressAvatar(image: ByteArray, maxPixels: Int, quality: Int): ByteArray =
    FileKit.compressImage(image, imageFormat = ImageFormat.JPEG, quality = quality, maxWidth = maxPixels, maxHeight = maxPixels)
