package io.github.solcott.activitytracker.activityfile

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SourceFileTest {
    private fun file(gzipped: Boolean, format: ActivityFileFormat = ActivityFileFormat.GPX) = ImportedFile(format, gzipped, emptyList())

    @Test
    fun aPlainFileIsGzippedAndComesBackByteForByte() = runTest {
        val bytes = buildString { repeat(5_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"/>\n") } }.encodeToByteArray()
        val source = assertNotNull(SourceFile.prepare(bytes, "Morning_Walk.gpx", file(gzipped = false)))

        assertTrue(source.compressed)
        assertTrue(source.stored.isGzip())
        assertEquals(ActivityFileFormat.GPX, source.format)
        assertEquals("Morning_Walk.gpx", source.fileName)
        assertEquals(bytes.size.toLong(), source.size)
        assertContentEquals(bytes, SourceFile.original(source.stored, source.compressed))
    }

    @Test
    fun anAlreadyGzippedFileIsStoredAndReturnedUnchanged() = runTest {
        val picked = gzip("<gpx/>".encodeToByteArray())
        val source = assertNotNull(SourceFile.prepare(picked, "12345.gpx.gz", file(gzipped = true)))

        assertFalse(source.compressed)
        assertContentEquals(picked, source.stored)
        assertEquals(picked.size.toLong(), source.size)
        assertContentEquals(picked, SourceFile.original(source.stored, source.compressed))
    }

    @Test
    fun anIncompressibleFileOverTheCapIsNotKept() = runTest {
        val noise = Random(11).nextBytes(11_000_000)
        assertNull(SourceFile.prepare(noise, "huge.fit", file(gzipped = false, ActivityFileFormat.FIT)))
    }

    @Test
    fun eachPreparedFileHasItsOwnKey() = runTest {
        val bytes = "<gpx/>".encodeToByteArray()
        val first = assertNotNull(SourceFile.prepare(bytes, "a.gpx", file(gzipped = false)))
        val second = assertNotNull(SourceFile.prepare(bytes, "a.gpx", file(gzipped = false)))
        assertNotEquals(first.key, second.key)
    }
}
