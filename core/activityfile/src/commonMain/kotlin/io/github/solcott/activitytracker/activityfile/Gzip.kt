package io.github.solcott.activitytracker.activityfile

/** Whether [this] starts with the gzip magic number (Strava's bulk export gzips every activity file). */
internal fun ByteArray.isGzip(): Boolean = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()

/**
 * Decompresses gzip: `java.util.zip` on Android and the JVM, the browser's `DecompressionStream` on web, kmp-zlib on iOS ([InflaterInput]).
 * Fails with [ActivityFileException] for corrupt or truncated data, or once the output passes [maxBytes] (a gzip bomb).
 */
internal expect suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray

/** Compresses [bytes] as gzip: `java.util.zip` on Android and the JVM, the browser's `CompressionStream` on web, kmp-zlib on iOS. */
internal expect suspend fun gzip(bytes: ByteArray): ByteArray

internal fun tooLargeError(maxBytes: Int): Nothing = throw FileTooLargeException(maxBytes)

/** A file, or its decompressed form, is past the size we read: [maxBytes]. */
class FileTooLargeException(val maxBytes: Int) : ActivityFileException("File is larger than ${maxBytes / BYTES_PER_MB} MB once unzipped")

private const val BYTES_PER_MB = 1024 * 1024
