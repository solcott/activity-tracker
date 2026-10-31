package io.github.solcott.activitytracker.activityfile

/** A stream of bytes read in order: a range of an [ImportSource], an archive member, or a member inflated as it's read. */
internal interface ByteInput {
    /** Reads up to [length] bytes into [into] at [offset]; returns how many, or -1 at the end. */
    suspend fun read(into: ByteArray, offset: Int, length: Int): Int
}

/** [length] bytes of [source] from [start], fetched [CHUNK_BYTES] at a time. */
internal class SourceInput(private val source: ImportSource, start: Long, length: Long) : ByteInput {
    private var position = start
    private val end = start + length

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (position >= end) return -1
        val wanted = minOf(length.toLong(), end - position, CHUNK_BYTES.toLong()).toInt()
        val bytes = source.read(position, wanted)
        if (bytes.isEmpty()) activityFileError("${source.name} ended early")
        bytes.copyInto(into, offset)
        position += bytes.size
        return bytes.size
    }
}

/** At most [limit] bytes of [input]: an archive member whose stored size is known. */
internal class LimitedInput(private val input: ByteInput, private var limit: Long) : ByteInput {
    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (limit <= 0) return -1
        val read = input.read(into, offset, minOf(length.toLong(), limit).toInt())
        if (read < 0) activityFileError("Archive member ended early")
        limit -= read
        return read
    }
}

/**
 * [input] read through a buffer, a byte at a time where that's simpler, with bytes read too far pushed back: the inflater peeks past the
 * end of its data, which a zip read as a stream needs back for the next header.
 */
internal class BufferedInput(private val input: ByteInput) : ByteInput {
    private var buffer = ByteArray(CHUNK_BYTES)
    private var position = 0
    private var limit = 0
    private var ended = false

    /** Bytes in the buffer, unread. */
    val buffered: Int
        get() = limit - position

    /** Fills the buffer to at least [count] bytes unless the input ends first; returns whether it holds that many. */
    suspend fun ensure(count: Int): Boolean {
        if (buffered >= count) return true
        if (position > 0) {
            buffer.copyInto(buffer, 0, position, limit)
            limit -= position
            position = 0
        }
        if (count > buffer.size) buffer = buffer.copyOf(count)
        while (limit < count && !ended) {
            val read = input.read(buffer, limit, buffer.size - limit)
            if (read < 0) ended = true else limit += read
        }
        return buffered >= count
    }

    /** The next buffered byte (0 to 255), or -1 when the buffer is empty; [ensure] first. */
    fun nextBuffered(): Int = if (position < limit) buffer[position++].toInt() and BYTE_MASK else -1

    /** Puts [bytes] back to be read next. */
    fun pushBack(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (position >= bytes.size) {
            position -= bytes.size
        } else {
            val merged = ByteArray(bytes.size + buffered)
            buffer.copyInto(merged, bytes.size, position, limit)
            buffer = if (merged.size >= CHUNK_BYTES) merged else merged.copyOf(CHUNK_BYTES)
            limit = merged.size
            position = 0
        }
        bytes.copyInto(buffer, position)
    }

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        val available = length > 0 && ensure(1)
        if (!available) return if (length == 0) 0 else -1
        val count = minOf(length, buffered)
        buffer.copyInto(into, offset, position, position + count)
        position += count
        return count
    }
}

/** Exactly [count] bytes, failing with [ActivityFileException] if the input ends first. */
internal suspend fun ByteInput.readExactly(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var done = 0
    while (done < count) {
        val read = read(bytes, done, count - done)
        if (read < 0) activityFileError("Archive ended early")
        done += read
    }
    return bytes
}

/** Reads and drops [count] bytes, failing if the input ends first. */
internal suspend fun ByteInput.skip(count: Long) {
    val scratch = ByteArray(minOf(count, CHUNK_BYTES.toLong()).toInt().coerceAtLeast(1))
    var left = count
    while (left > 0) {
        val read = read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
        if (read < 0) activityFileError("Archive ended early")
        left -= read
    }
}

/** Reads to the end and drops it. */
internal suspend fun ByteInput.drain() {
    val scratch = ByteArray(CHUNK_BYTES)
    do {
        val n = read(scratch, 0, scratch.size)
    } while (n >= 0)
}

/** Everything left, failing with [FileTooLargeException] past [maxBytes]. */
internal suspend fun ByteInput.readAll(maxBytes: Int): ByteArray {
    var bytes = ByteArray(minOf(maxBytes, CHUNK_BYTES))
    var size = 0
    while (true) {
        if (size == bytes.size) {
            if (size >= maxBytes) {
                // Full at the cap: anything more is too much.
                val probe = ByteArray(1)
                if (read(probe, 0, 1) < 0) return bytes
                tooLargeError(maxBytes)
            }
            bytes = bytes.copyOf(minOf(maxBytes.toLong(), size * 2L).toInt())
        }
        val read = read(bytes, size, bytes.size - size)
        if (read < 0) return bytes.copyOf(size)
        size += read
    }
}

/** Little-endian integers in archive headers. */
internal fun ByteArray.u16(at: Int): Int = (this[at].toInt() and BYTE_MASK) or ((this[at + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

internal fun ByteArray.u32(at: Int): Long = (u16(at).toLong()) or (u16(at + U16_BYTES).toLong() shl (U16_BYTES * BITS_PER_BYTE))

internal fun ByteArray.u64(at: Int): Long = u32(at) or (u32(at + U32_BYTES) shl (U32_BYTES * BITS_PER_BYTE))

private const val U16_BYTES = 2
private const val U32_BYTES = 4

/** How much of a file is fetched per read: big enough to keep reads few, small enough for any phone. */
internal const val CHUNK_BYTES = 64 * 1024

internal const val BYTE_MASK = 0xFF
internal const val BITS_PER_BYTE = 8
