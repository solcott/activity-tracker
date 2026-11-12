package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.edit.PhotoCompression
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [image] (any format the platform decodes) upright, scaled to at most [maxPixels] on its long edge and encoded lossily at [quality]
 * (0–100). Re-encoding drops the metadata, the GPS position included: records are public.
 */
internal expect fun encodePhoto(image: ByteArray, maxPixels: Int, quality: Int): CompressedPhoto

/**
 * [image] ready to attach to an activity: WebP through [PhotoCompression]'s steps, the first under its target, else the last. The iOS app's
 * native pick runs the same steps in Swift with libwebp (`PhotoCompressor`).
 */
suspend fun compressPhoto(image: ByteArray, dispatcher: CoroutineDispatcher = Dispatchers.Default): CompressedPhoto =
    withContext(dispatcher) {
        val steps = PhotoCompression.steps
        steps
            .dropLast(1)
            .asSequence()
            .map { encodePhoto(image, it.maxPixels, it.quality) }
            .firstOrNull { it.bytes.size <= PhotoCompression.targetBytes }
            ?: steps.last().let { encodePhoto(image, it.maxPixels, it.quality) }
    }

internal const val WEBP = "image/webp"
internal const val JPEG = "image/jpeg"

/** For a platform that can't write WebP. */
internal const val JPEG_QUALITY = 82
