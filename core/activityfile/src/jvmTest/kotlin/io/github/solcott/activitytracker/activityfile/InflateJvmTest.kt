package io.github.solcott.activitytracker.activityfile

import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class InflateJvmTest {
    private val text = buildString { repeat(30_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"><ele>${it % 300}</ele></trkpt>\n") } }

    @Test
    fun inflatesWhatJavaDeflates() = runTest {
        val inputs =
            listOf(
                ByteArray(0),
                "a".encodeToByteArray(),
                "hello hello hello hello".encodeToByteArray(),
                text.encodeToByteArray(),
                Random(3).nextBytes(150_000),
                ByteArray(200_000) { 7 },
            )
        for (input in inputs) {
            for (level in listOf(Deflater.NO_COMPRESSION, Deflater.BEST_SPEED, Deflater.DEFAULT_COMPRESSION, Deflater.BEST_COMPRESSION)) {
                val inflated = inflate(deflate(input, level))
                assertContentEquals(input, inflated, "${input.size} bytes at level $level")
            }
        }
    }

    @Test
    fun inflatesHuffmanOnlyAndFilteredStrategies() = runTest {
        for (strategy in listOf(Deflater.HUFFMAN_ONLY, Deflater.FILTERED)) {
            val input = text.encodeToByteArray()
            assertContentEquals(input, inflate(deflate(input, Deflater.DEFAULT_COMPRESSION, strategy)))
        }
    }

    @Test
    fun leavesTheBytesAfterTheDeflateDataUnread() = runTest {
        val compressed = deflate(text.encodeToByteArray(), Deflater.DEFAULT_COMPRESSION)
        val trailer = "PK-next-header".encodeToByteArray()
        val input = BufferedInput(ChunkedInput(compressed + trailer, chunk = 777))
        assertContentEquals(text.encodeToByteArray(), InflaterInput(input).readAll(Int.MAX_VALUE))
        assertContentEquals(trailer, input.readAll(Int.MAX_VALUE))
    }

    @Test
    fun failsOnTruncatedData() = runTest {
        val compressed = deflate(text.encodeToByteArray(), Deflater.DEFAULT_COMPRESSION)
        assertFailsWith<ActivityFileException> { inflate(compressed.copyOf(compressed.size / 2)) }
    }

    @Test
    fun failsOnCorruptData() = runTest {
        // Block type 3 is reserved.
        assertFailsWith<ActivityFileException> { inflate(byteArrayOf(0x07, 0x00)) }
    }

    @Test
    fun stopsAtTheCap() = runTest {
        val compressed = deflate(ByteArray(5_000_000), Deflater.BEST_COMPRESSION)
        assertFailsWith<FileTooLargeException> { InflaterInput(BufferedInput(ChunkedInput(compressed, 4096))).readAll(1_000_000) }
    }

    private suspend fun inflate(bytes: ByteArray): ByteArray =
        InflaterInput(BufferedInput(ChunkedInput(bytes, chunk = 1000))).readAll(Int.MAX_VALUE)

    private fun deflate(input: ByteArray, level: Int, strategy: Int = Deflater.DEFAULT_STRATEGY): ByteArray {
        val deflater = Deflater(level, true).apply { setStrategy(strategy) }
        deflater.setInput(input)
        deflater.finish()
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }
}

/** [bytes] handed out at most [chunk] at a time, as a file or network read would. */
internal class ChunkedInput(private val bytes: ByteArray, private val chunk: Int) : ByteInput {
    private var position = 0

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length, chunk, bytes.size - position)
        bytes.copyInto(into, offset, position, position + count)
        position += count
        return count
    }
}
