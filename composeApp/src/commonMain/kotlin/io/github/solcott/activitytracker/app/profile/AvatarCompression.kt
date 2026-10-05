package io.github.solcott.activitytracker.app.profile

/** [image] (any format the platform decodes) as a JPEG no larger than [maxPixels] on its longer side. */
expect suspend fun compressAvatar(image: ByteArray, maxPixels: Int, quality: Int): ByteArray
