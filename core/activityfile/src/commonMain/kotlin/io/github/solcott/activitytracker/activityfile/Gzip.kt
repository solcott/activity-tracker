package io.github.solcott.activitytracker.activityfile

/** Whether [this] starts with the gzip magic number (Strava's bulk export gzips every activity file). */
internal fun ByteArray.isGzip(): Boolean = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()

/**
 * Decompresses gzip with the platform's own inflater: `java.util.zip` on the JVM, zlib on iOS, the browser's `DecompressionStream` on web.
 * Fails with [ActivityFileException] for corrupt or truncated data, or once the output passes [maxBytes] (a gzip bomb).
 */
internal expect suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray

/**
 * Compresses [bytes] as gzip with the platform's own deflater: `java.util.zip` on the JVM, zlib on iOS, the browser's `CompressionStream`
 * on web.
 */
internal expect suspend fun gzip(bytes: ByteArray): ByteArray

internal fun tooLargeError(maxBytes: Int): Nothing = activityFileError("File is larger than ${maxBytes / BYTES_PER_MB} MB once unzipped")

private const val BYTES_PER_MB = 1024 * 1024
