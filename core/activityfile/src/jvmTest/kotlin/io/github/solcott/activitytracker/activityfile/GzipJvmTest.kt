package io.github.solcott.activitytracker.activityfile

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.test.runTest

class GzipJvmTest {
    @Test
    fun roundTripsDataLargerThanOneChunk() = runTest {
        val original = buildString { repeat(20_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"/>\n") } }.encodeToByteArray()
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(original) } }.toByteArray()

        assertContentEquals(original, gunzip(zipped, maxBytes = original.size))
    }
}
