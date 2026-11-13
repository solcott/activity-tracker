package io.github.solcott.activitytracker.app.ui

/**
 * [image] (any format the platform decodes) as a JPEG no larger than [maxPixels] on its longer side. The iOS app makes its own with the
 * same FileKit call this makes on Android and desktop (`IosApp.compressAvatar`).
 */
expect suspend fun compressAvatar(image: ByteArray, maxPixels: Int, quality: Int): ByteArray
