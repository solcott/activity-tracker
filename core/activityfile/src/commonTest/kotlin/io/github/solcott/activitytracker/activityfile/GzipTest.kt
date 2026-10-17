package io.github.solcott.activitytracker.activityfile

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GzipTest {
    @Test
    fun roundTripsThroughGzipAndGunzip() = runTest {
        val text = buildString { repeat(20_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"/>\n") } }.encodeToByteArray()
        val zipped = gzip(text)
        assertTrue(zipped.isGzip())
        assertTrue(zipped.size < text.size / 4, "text compresses")
        assertContentEquals(text, gunzip(zipped, maxBytes = text.size))

        val noise = Random(7).nextBytes(200_000)
        assertContentEquals(noise, gunzip(gzip(noise), maxBytes = noise.size))
        assertContentEquals(ByteArray(0), gunzip(gzip(ByteArray(0)), maxBytes = 0))
    }
}
