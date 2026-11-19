package io.github.solcott.activitytracker.appview.ipld

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/** How much of a CAR the AppView reads before giving up. */
internal data class CarLimits(val maxBytes: Long, val maxBlocks: Int)

/**
 * A CAR v1 file (`com.atproto.sync.getRepo`'s answer), copied to a temp file while counting it against [CarLimits], with an index of where
 * each block is. Blocks are read back one at a time and checked against their CID, so a repo far larger than memory can be walked, and a
 * block whose bytes don't match its CID is never used. Close it to delete the temp file.
 */
internal class CarFile private constructor(private val path: Path, val root: BlockCid, private val index: Map<BlockCid, Section>) :
    Closeable {
    private val file = RandomAccessFile(path.toFile(), "r")

    val blockCount: Int
        get() = index.size

    /** The CAR's size in bytes, as fetched. */
    val byteCount: Long
        get() = file.length()

    /** The bytes of [cid]'s block, checked against it. Throws [RepoFormatException] when the CAR lacks it or it doesn't match. */
    fun block(cid: BlockCid): ByteArray {
        val section = index[cid] ?: throw RepoFormatException("missing_block", "The repo lacks a block it names")
        val bytes = ByteArray(section.length)
        synchronized(file) {
            file.seek(section.offset)
            file.readFully(bytes)
        }
        if (!cid.matches(bytes)) throw RepoFormatException("bad_block", "A block doesn't match its CID")
        return bytes
    }

    override fun close() {
        runCatching { file.close() }
        path.deleteIfExists()
    }

    private class Section(val offset: Long, val length: Int)

    companion object {
        private const val CAR_VERSION = 1L

        /** A header is a small map; anything larger is refused rather than buffered. */
        private const val MAX_HEADER_BYTES = 4096L

        /** No block of an atproto repo comes near this (records are capped far below). */
        private const val MAX_BLOCK_BYTES = 2L * 1024 * 1024

        /**
         * Copies [input] to a temp file in [dir] (the system's when null), then indexes it. Throws [RepoLimitException] past [limits] and
         * [RepoFormatException] for anything that isn't a CAR v1 with one root; the temp file is deleted on failure.
         */
        fun read(input: InputStream, limits: CarLimits, dir: Path? = null): CarFile {
            val path = if (dir != null) Files.createTempFile(dir, "repo-", ".car") else Files.createTempFile("repo-", ".car")
            try {
                Files.newOutputStream(path).use { out -> CappedInputStream(input, limits.maxBytes).copyTo(out) }
                val (root, index) = BufferedInputStream(Files.newInputStream(path)).use { indexOf(CountingInputStream(it), limits) }
                return CarFile(path, root, index)
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                path.deleteIfExists()
                throw e
            }
        }

        @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
        private fun indexOf(input: CountingInputStream, limits: CarLimits): Pair<BlockCid, Map<BlockCid, Section>> {
            val headerLength = Varint.read(input)
            if (headerLength !in 1..MAX_HEADER_BYTES) throw RepoFormatException("bad_car", "Bad CAR header length")
            val header = DagCbor.decode(input.readExactly(headerLength.toInt())) as? Map<*, *>
            val roots = header?.get("roots") as? List<*>
            if (header?.get("version") != CAR_VERSION || roots?.size != 1)
                throw RepoFormatException("bad_car", "Not a CAR v1 with one root")
            val root = (roots.single() as? DagCbor.Link)?.cid ?: throw RepoFormatException("bad_car", "CAR root isn't a CID")
            val index = HashMap<BlockCid, Section>()
            while (true) {
                val length = Varint.readOrEnd(input) ?: break
                if (length !in 1..MAX_BLOCK_BYTES) throw RepoFormatException("bad_car", "Bad CAR section length")
                val start = input.count
                val cid = BlockCid.read(input)
                val dataLength = length - (input.count - start)
                if (dataLength < 0) throw RepoFormatException("bad_car", "CAR section shorter than its CID")
                if (index.size >= limits.maxBlocks && cid !in index) {
                    throw RepoLimitException("too_many_blocks", "The repo has more than ${limits.maxBlocks} blocks")
                }
                index[cid] = Section(input.count, dataLength.toInt())
                input.skipExactly(dataLength)
            }
            return root to index
        }
    }
}

/** Fails with [RepoLimitException] once more than [max] bytes were read. */
private class CappedInputStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
    private var read = 0L

    override fun read(): Int = super.read().also { if (it >= 0) count(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

    private fun count(n: Long) {
        read += n
        if (read > max) throw RepoLimitException("too_large", "The repo is larger than $max bytes")
    }
}

/** Counts the bytes read, so section offsets in the file are known. */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

    override fun skip(n: Long): Long = super.skip(n).also { count += it }

    fun readExactly(n: Int): ByteArray = readNBytes(n).also { if (it.size != n) throw RepoFormatException("truncated", "Truncated CAR") }

    fun skipExactly(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() == -1) throw RepoFormatException("truncated", "Truncated CAR")
                left--
            } else {
                left -= skipped
            }
        }
    }
}
