package io.github.solcott.activitytracker.app.ui

/** A photo re-encoded for upload ([compressPhoto]): [mimeType] is WebP, or JPEG where WebP can't be written, and its size after scaling. */
class CompressedPhoto(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int)
