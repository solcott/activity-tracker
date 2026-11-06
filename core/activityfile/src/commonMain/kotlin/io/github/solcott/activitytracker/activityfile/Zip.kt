package io.github.solcott.activitytracker.activityfile

import no.synth.kmpzlib.Crc32

/** A file in a zip: its [path] in the archive, how it's stored, and its sizes and CRC-32 as the archive declares them. */
@Suppress("LongParameterList") // One per header field.
internal class ZipEntry(
    val path: String,
    val method: Int,
    val flags: Int,
    val compressedSize: Long,
    val size: Long,
    /** Its data's CRC-32; 0 in a local header whose sizes follow its data, where the data descriptor holds it. */
    val crc: Long = 0,
    /** Where its local header starts; -1 for an entry read from a stream. */
    val localHeaderOffset: Long = -1,
) {
    val isDirectory: Boolean
        get() = path.endsWith('/')

    val isEncrypted: Boolean
        get() = flags and FLAG_ENCRYPTED != 0

    /** Whether we can decompress it: stored or deflated, and not encrypted. */
    val isSupported: Boolean
        get() = !isEncrypted && (method == METHOD_STORED || method == METHOD_DEFLATED)

    /** The file name without its folders. */
    val name: String
        get() = path.substringAfterLast('/')
}

/** Whether [bytes] (a file's first bytes) start a zip: a local file header, or an empty archive's end record. */
internal fun isZip(bytes: ByteArray): Boolean =
    bytes.size >= SIGNATURE_BYTES && (bytes.u32(0) == LOCAL_HEADER_SIGNATURE || bytes.u32(0) == END_OF_CENTRAL_DIRECTORY_SIGNATURE)

/**
 * A zip read through its central directory, so any member can be opened without reading the ones before it. ZIP64 archives (over 4 GB, or
 * over 65,535 members) are read too. Fails with [ActivityFileException] when the archive is damaged.
 */
internal class ZipArchive private constructor(private val source: ImportSource, val entries: List<ZipEntry>) {
    /** [entry]'s data, decompressed as it's read and checked against its CRC-32 once read to the end. */
    suspend fun open(entry: ZipEntry): ByteInput {
        if (!entry.isSupported) throw UnsupportedArchiveEntryException(entry.path)
        val header = source.read(entry.localHeaderOffset, LOCAL_HEADER_SIZE)
        if (header.size < LOCAL_HEADER_SIZE || header.u32(0) != LOCAL_HEADER_SIGNATURE) damaged()
        val dataStart = entry.localHeaderOffset + LOCAL_HEADER_SIZE + header.u16(LOCAL_NAME_LENGTH) + header.u16(LOCAL_EXTRA_LENGTH)
        val data = SourceInput(source, dataStart, entry.compressedSize)
        val plain = if (entry.method == METHOD_STORED) data else InflaterInput(BufferedInput(data))
        return CheckedInput(plain, entry.path) { entry.crc }
    }

    companion object {
        /** Reads [source]'s central directory; at most [maxEntries] are listed, and [truncated] says whether there were more. */
        suspend fun read(source: ImportSource, maxEntries: Int = MAX_ARCHIVE_MEMBERS): Listing {
            val end = findEnd(source)
            var entryCount = end.u16(EOCD_ENTRIES).toLong()
            var directorySize = end.u32(EOCD_DIRECTORY_SIZE)
            var directoryOffset = end.u32(EOCD_DIRECTORY_OFFSET)
            if (entryCount == U16_MAX || directorySize == U32_MAX || directoryOffset == U32_MAX) {
                val zip64 = readZip64End(source, end.offset)
                entryCount = zip64.u64(ZIP64_EOCD_ENTRIES)
                directorySize = zip64.u64(ZIP64_EOCD_DIRECTORY_SIZE)
                directoryOffset = zip64.u64(ZIP64_EOCD_DIRECTORY_OFFSET)
            }
            if (directoryOffset + directorySize > source.size) damaged()
            val entries = mutableListOf<ZipEntry>()
            val input = BufferedInput(SourceInput(source, directoryOffset, directorySize))
            var index = 0L
            while (index < entryCount && entries.size < maxEntries) {
                val header = input.readExactly(CENTRAL_HEADER_SIZE)
                if (header.u32(0) != CENTRAL_HEADER_SIGNATURE) damaged()
                val path = input.readExactly(header.u16(CENTRAL_NAME_LENGTH)).decodeToString()
                val extra = input.readExactly(header.u16(CENTRAL_EXTRA_LENGTH))
                input.skip(header.u16(CENTRAL_COMMENT_LENGTH).toLong())
                var size = header.u32(CENTRAL_SIZE)
                var compressedSize = header.u32(CENTRAL_COMPRESSED_SIZE)
                var offset = header.u32(CENTRAL_LOCAL_OFFSET)
                zip64Extra(extra)?.let { values ->
                    // The ZIP64 field holds, in this order, only the values the header marks with all ones.
                    var at = 0
                    fun next(): Long = values.u64(at).also { at += U64_BYTES }
                    if (size == U32_MAX) size = next()
                    if (compressedSize == U32_MAX) compressedSize = next()
                    if (offset == U32_MAX) offset = next()
                }
                entries +=
                    ZipEntry(
                        path = path,
                        method = header.u16(CENTRAL_METHOD),
                        flags = header.u16(CENTRAL_FLAGS),
                        compressedSize = compressedSize,
                        size = size,
                        crc = header.u32(CENTRAL_CRC),
                        localHeaderOffset = offset,
                    )
                index++
            }
            return Listing(ZipArchive(source, entries), truncated = index < entryCount)
        }

        private class End(val bytes: ByteArray, val at: Int, val offset: Long) {
            fun u16(field: Int) = bytes.u16(at + field)

            fun u32(field: Int) = bytes.u32(at + field)
        }

        /** The end-of-central-directory record, searched for backwards past any archive comment. */
        private suspend fun findEnd(source: ImportSource): End {
            val tailLength = minOf(source.size, (EOCD_SIZE + MAX_COMMENT_LENGTH).toLong()).toInt()
            val tailStart = source.size - tailLength
            val tail = source.read(tailStart, tailLength)
            for (at in tail.size - EOCD_SIZE downTo 0) {
                if (tail.u32(at) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) return End(tail, at, tailStart + at)
            }
            damaged()
        }

        private suspend fun readZip64End(source: ImportSource, endOffset: Long): ByteArray {
            if (endOffset < ZIP64_LOCATOR_SIZE) damaged()
            val locator = source.read(endOffset - ZIP64_LOCATOR_SIZE, ZIP64_LOCATOR_SIZE)
            if (locator.u32(0) != ZIP64_LOCATOR_SIGNATURE) damaged()
            val record = source.read(locator.u64(ZIP64_LOCATOR_OFFSET), ZIP64_EOCD_SIZE)
            if (record.size < ZIP64_EOCD_SIZE || record.u32(0) != ZIP64_EOCD_SIGNATURE) damaged()
            return record
        }
    }

    /** A zip's [archive], and whether its listing stopped at the member limit ([truncated]). */
    class Listing(val archive: ZipArchive, val truncated: Boolean)
}

/**
 * A zip read from the start, header by header, for one that arrives as a stream (a zip inside a zip). Each [next] entry's [Entry.data] is
 * readable until the following call. A deflated member whose sizes follow its data still reads, since deflate data ends itself; a stored
 * one can't be found the end of, so the walk ends there ([ZipStreamEndedException]).
 */
internal class ZipStream(input: ByteInput) {
    private val input = input as? BufferedInput ?: BufferedInput(input)
    private var current: Entry? = null

    class Entry(val entry: ZipEntry, val data: ByteInput, internal val skipRest: suspend () -> Unit)

    /** The next member, or null after the last. */
    suspend fun next(): Entry? {
        current?.skipRest?.invoke()
        current = null
        val header = readLocalHeader() ?: return null
        return (if (header.flags and FLAG_DATA_DESCRIPTOR != 0) entryWithSizesAfter(header) else entryWithSizes(header)).also {
            current = it
        }
    }

    private class LocalHeader(val entry: ZipEntry, val flags: Int, val zip64: Boolean)

    /** The next local header, or null at the central directory or the end: no more members. */
    private suspend fun readLocalHeader(): LocalHeader? {
        val signature = if (input.ensure(SIGNATURE_BYTES)) input.readExactly(SIGNATURE_BYTES) else null
        if (signature == null || signature.u32(0) != LOCAL_HEADER_SIGNATURE) return null
        val header = signature + input.readExactly(LOCAL_HEADER_SIZE - SIGNATURE_BYTES)
        val path = input.readExactly(header.u16(LOCAL_NAME_LENGTH)).decodeToString()
        val zip64 = zip64Extra(input.readExactly(header.u16(LOCAL_EXTRA_LENGTH)))?.takeIf { it.size >= 2 * U64_BYTES }
        val flags = header.u16(LOCAL_FLAGS)
        val entry =
            ZipEntry(
                path = path,
                method = header.u16(LOCAL_METHOD),
                flags = flags,
                compressedSize = zip64?.u64(U64_BYTES) ?: header.u32(LOCAL_COMPRESSED_SIZE),
                size = zip64?.u64(0) ?: header.u32(LOCAL_SIZE),
                crc = header.u32(LOCAL_CRC),
            )
        return LocalHeader(entry, flags, zip64 != null)
    }

    /** A member whose sizes follow its data: only deflate data says where it ends. */
    private fun entryWithSizesAfter(header: LocalHeader): Entry {
        val entry = header.entry
        if (entry.method != METHOD_DEFLATED || entry.isEncrypted) throw ZipStreamEndedException(entry.path)
        // Read to its end, the data is followed by the descriptor holding its CRC-32; skipping it reads that too.
        val data = CheckedInput(InflaterInput(input), entry.path) { readDataDescriptor(header.zip64) }
        return Entry(entry, data) { data.drain() }
    }

    /** A member whose stored size is in its header; an unsupported one is skipped by that size, never decompressed. */
    private fun entryWithSizes(header: LocalHeader): Entry {
        val entry = header.entry
        val raw = LimitedInput(input, entry.compressedSize)
        val data: ByteInput =
            when {
                !entry.isSupported -> EmptyInput
                entry.method == METHOD_STORED -> CheckedInput(raw, entry.path) { entry.crc }
                else -> CheckedInput(InflaterInput(BufferedInput(raw)), entry.path) { entry.crc }
            }
        return Entry(entry, data) { raw.drain() }
    }

    /** Reads the data descriptor after a member's data; returns its CRC-32. */
    private suspend fun readDataDescriptor(zip64: Boolean): Long {
        val first = input.readExactly(SIGNATURE_BYTES).u32(0)
        // The descriptor's signature is optional: without it, the four bytes read were the CRC.
        val crc = if (first == DATA_DESCRIPTOR_SIGNATURE) input.readExactly(CRC_BYTES).u32(0) else first
        input.skip(2L * (if (zip64) U64_BYTES else U32_BYTES))
        return crc
    }

    private object EmptyInput : ByteInput {
        override suspend fun read(into: ByteArray, offset: Int, length: Int): Int = -1
    }
}

/** The values of a header's ZIP64 extra field, or null when it has none. */
private fun zip64Extra(extra: ByteArray): ByteArray? {
    var at = 0
    var found: ByteArray? = null
    // Each field is a two-byte ID and a two-byte length, then its data.
    while (found == null && at + EXTRA_FIELD_HEADER_BYTES <= extra.size) {
        val dataStart = at + EXTRA_FIELD_HEADER_BYTES
        val dataEnd = dataStart + extra.u16(at + EXTRA_FIELD_LENGTH_OFFSET)
        if (dataEnd > extra.size) break
        if (extra.u16(at) == ZIP64_EXTRA_ID) found = extra.copyOfRange(dataStart, dataEnd)
        at = dataEnd
    }
    return found
}

/**
 * [input], a member's data, with its CRC-32 worked out as it's read: once read to the end, it's compared with [expected] (read then, as a
 * data descriptor follows the data), failing with [ActivityFileException] when they differ. A member read only in part isn't checked.
 */
private class CheckedInput(private val input: ByteInput, private val path: String, private val expected: suspend () -> Long) : ByteInput {
    private val crc = Crc32()
    private var checked = false

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        val read = input.read(into, offset, length)
        if (read > 0) crc.update(into, offset, read)
        if (read < 0 && !checked) {
            checked = true
            if (crc.value != expected()) activityFileError("Damaged file in zip archive: $path")
        }
        return read
    }
}

private fun damaged(): Nothing = activityFileError("Damaged zip archive")

/** A zip member stored in a way we don't read: compressed with a method other than deflate, or encrypted. */
class UnsupportedArchiveEntryException(path: String) : ActivityFileException("Unsupported compression in $path")

/** A zip read as a stream reached a member whose end can't be found; it and the members after it can't be read. */
internal class ZipStreamEndedException(path: String) : ActivityFileException("Can't read past $path in a nested zip")

/** Members listed per archive, far beyond any account export; stops an archive built to exhaust memory with its listing. */
internal const val MAX_ARCHIVE_MEMBERS = 100_000

internal const val METHOD_STORED = 0
internal const val METHOD_DEFLATED = 8

private const val SIGNATURE_BYTES = 4
private const val CRC_BYTES = 4
private const val U32_BYTES = 4
private const val U64_BYTES = 8
private const val EXTRA_FIELD_HEADER_BYTES = 4
private const val EXTRA_FIELD_LENGTH_OFFSET = 2

private const val FLAG_ENCRYPTED = 1
private const val FLAG_DATA_DESCRIPTOR = 1 shl 3

private const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50L
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50L
private const val ZIP64_EOCD_SIGNATURE = 0x06064b50L
private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
private const val ZIP64_EXTRA_ID = 0x0001

private const val LOCAL_HEADER_SIZE = 30
private const val LOCAL_FLAGS = 6
private const val LOCAL_METHOD = 8
private const val LOCAL_CRC = 14
private const val LOCAL_COMPRESSED_SIZE = 18
private const val LOCAL_SIZE = 22
private const val LOCAL_NAME_LENGTH = 26
private const val LOCAL_EXTRA_LENGTH = 28

private const val CENTRAL_HEADER_SIZE = 46
private const val CENTRAL_FLAGS = 8
private const val CENTRAL_METHOD = 10
private const val CENTRAL_CRC = 16
private const val CENTRAL_COMPRESSED_SIZE = 20
private const val CENTRAL_SIZE = 24
private const val CENTRAL_NAME_LENGTH = 28
private const val CENTRAL_EXTRA_LENGTH = 30
private const val CENTRAL_COMMENT_LENGTH = 32
private const val CENTRAL_LOCAL_OFFSET = 42

private const val EOCD_SIZE = 22
private const val EOCD_ENTRIES = 10
private const val EOCD_DIRECTORY_SIZE = 12
private const val EOCD_DIRECTORY_OFFSET = 16
private const val MAX_COMMENT_LENGTH = 0xFFFF

private const val ZIP64_LOCATOR_SIZE = 20
private const val ZIP64_LOCATOR_OFFSET = 8
private const val ZIP64_EOCD_SIZE = 56
private const val ZIP64_EOCD_ENTRIES = 32
private const val ZIP64_EOCD_DIRECTORY_SIZE = 40
private const val ZIP64_EOCD_DIRECTORY_OFFSET = 48

private const val U16_MAX = 0xFFFFL
private const val U32_MAX = 0xFFFFFFFFL
