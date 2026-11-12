package io.github.solcott.activitytracker.app.edit

import androidx.compose.runtime.Immutable

/**
 * How a picked activity or gear photo is compressed for upload, wherever it is compressed: the shared UI's `compressPhoto` and the iOS
 * app's `PhotoCompressor` (libwebp, in Swift) both run these steps, so the photo saved is the same size on every platform. Each step scales
 * the photo upright to at most [PhotoStep.maxPixels] on its long edge and encodes it as lossy WebP at [PhotoStep.quality]; the first under
 * [targetBytes] is kept, and the last is kept whatever its size. The target is comfortably inside the lexicon's 2 MB.
 */
object PhotoCompression {
    const val targetBytes: Int = 1_500_000

    /** In order, the last being the fallback. A getter, so the object holds no unstable field (strict stability). */
    val steps: List<PhotoStep>
        get() = STEPS
}

private val STEPS = listOf(PhotoStep(2048, 80), PhotoStep(2048, 70), PhotoStep(1600, 70), PhotoStep(1280, 60))

/** One of [PhotoCompression.steps]: the long edge's pixels and the WebP quality (0–100). */
@Immutable data class PhotoStep(val maxPixels: Int, val quality: Int)
