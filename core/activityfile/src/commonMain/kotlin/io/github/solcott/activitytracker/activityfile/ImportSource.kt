package io.github.solcott.activitytracker.activityfile

import androidx.compose.runtime.Stable

/**
 * A picked file, read by range so an archive of several gigabytes is never held whole: the import screen adapts each platform's file handle
 * to it (FileKit's `source()` off the web, `Blob.slice` on web). Stable: each is one picked file, read but never changed.
 */
@Stable
interface ImportSource {
    /** The file's name as picked, which names its original if an activity is saved from it. */
    val name: String

    /** Its length in bytes. */
    val size: Long

    /** [length] bytes from [offset], fewer only at the end of the file. Fails with [ActivityFileException] once the file can't be read. */
    suspend fun read(offset: Long, length: Int): ByteArray
}

/** An [ImportSource] over bytes already in memory: a file read whole, an archive member, or a test's fixture. */
class ByteArrayImportSource(override val name: String, private val bytes: ByteArray) : ImportSource {
    override val size: Long
        get() = bytes.size.toLong()

    override suspend fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0) { "Negative range" }
        if (offset >= bytes.size) return ByteArray(0)
        val start = offset.toInt()
        return bytes.copyOfRange(start, minOf(bytes.size, start + length))
    }
}

/** All of [this], for a file small enough to decode whole; fails with [FileTooLargeException] past [maxBytes]. */
internal suspend fun ImportSource.readAll(maxBytes: Int): ByteArray {
    if (size > maxBytes) tooLargeError(maxBytes)
    return SourceInput(this, 0, size).readAll(maxBytes)
}
