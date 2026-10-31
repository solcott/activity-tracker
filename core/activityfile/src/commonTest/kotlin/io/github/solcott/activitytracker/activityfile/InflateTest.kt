package io.github.solcott.activitytracker.activityfile

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.test.runTest

class InflateTest {
    @Test
    fun inflatesThePlatformsOwnDeflate() = runTest {
        val text = buildString { repeat(20_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"/>\n") } }.encodeToByteArray()
        for (input in listOf(text, Random(11).nextBytes(100_000), ByteArray(0))) {
            // A gzip member is a 10-byte header, raw deflate, then an 8-byte trailer.
            val zipped = gzip(input)
            val raw = zipped.copyOfRange(GZIP_HEADER_BYTES, zipped.size - GZIP_TRAILER_BYTES)
            assertContentEquals(
                input,
                InflaterInput(BufferedInput(SourceInput(ByteArrayImportSource("x", raw), 0, raw.size.toLong()))).readAll(input.size),
            )
        }
    }

    private companion object {
        const val GZIP_HEADER_BYTES = 10
        const val GZIP_TRAILER_BYTES = 8
    }
}
