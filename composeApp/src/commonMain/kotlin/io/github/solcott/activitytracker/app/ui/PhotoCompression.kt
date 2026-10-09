package io.github.solcott.activitytracker.app.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [image] (any format the platform decodes) upright, scaled to at most [maxPixels] on its long edge and encoded lossily at [quality]
 * (0–100). Re-encoding drops the metadata, the GPS position included: records are public.
 */
internal expect fun encodePhoto(image: ByteArray, maxPixels: Int, quality: Int): CompressedPhoto

/**
 * [image] ready to attach to an activity: WebP at 2048 px and quality 80, stepping down until it's under [TARGET_BYTES], comfortably inside
 * the lexicon's 2 MB.
 */
suspend fun compressPhoto(image: ByteArray, dispatcher: CoroutineDispatcher = Dispatchers.Default): CompressedPhoto =
    withContext(dispatcher) {
        STEPS.asSequence().map { (pixels, quality) -> encodePhoto(image, pixels, quality) }.firstOrNull { it.bytes.size <= TARGET_BYTES }
            ?: encodePhoto(image, LAST_RESORT_PIXELS, LAST_RESORT_QUALITY)
    }

internal const val WEBP = "image/webp"
internal const val JPEG = "image/jpeg"

/** For a platform that can't write WebP. */
internal const val JPEG_QUALITY = 82

private const val TARGET_BYTES = 1_500_000
private val STEPS = listOf(2048 to 80, 2048 to 70, 1600 to 70)
private const val LAST_RESORT_PIXELS = 1280
private const val LAST_RESORT_QUALITY = 60
