package io.github.solcott.activitytracker.activityfile

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ZipJvmTest {
    private val gpx = buildString { repeat(5_000) { append("<trkpt lat=\"47.$it\" lon=\"-122.3\"/>\n") } }.encodeToByteArray()
    private val fit = ByteArray(40_000) { (it * 31).toByte() }

    @Test
    fun readsStoredAndDeflatedMembers() = runTest {
        val zip = zipOf("rides/a.gpx" to gpx, "b.fit" to fit, stored = setOf("b.fit"), folders = listOf("rides/"))
        val listing = ZipArchive.read(ByteArrayImportSource("x.zip", zip))
        assertFalse(listing.truncated)
        val files = listing.archive.entries.filterNot { it.isDirectory }
        assertEquals(listOf("rides/a.gpx", "b.fit"), files.map { it.path })
        assertEquals(listOf(METHOD_DEFLATED, METHOD_STORED), files.map { it.method })
        assertEquals("a.gpx", files[0].name)
        assertContentEquals(gpx, listing.archive.open(files[0]).readAll(Int.MAX_VALUE))
        assertContentEquals(fit, listing.archive.open(files[1]).readAll(Int.MAX_VALUE))
    }

    @Test
    fun readsAZip64Archive() = runTest {
        // Over 65,535 members makes java.util.zip write the ZIP64 end records.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            repeat(65_600) {
                zip.putNextEntry(JavaZipEntry("e$it"))
                zip.closeEntry()
            }
            zip.putNextEntry(JavaZipEntry("last.gpx"))
            zip.write(gpx)
            zip.closeEntry()
        }
        val listing = ZipArchive.read(ByteArrayImportSource("big.zip", out.toByteArray()))
        assertEquals(65_601, listing.archive.entries.size)
        val last = listing.archive.entries.last()
        assertContentEquals(gpx, listing.archive.open(last).readAll(Int.MAX_VALUE))
    }

    @Test
    fun reportsAnUnsupportedMethod() = runTest {
        val zip = zipOf("a.gpx" to gpx, stored = setOf("a.gpx")).withMethod(12) // bzip2
        val entry = ZipArchive.read(ByteArrayImportSource("x.zip", zip)).archive.entries.single()
        assertFalse(entry.isSupported)
        assertFailsWith<UnsupportedArchiveEntryException> { ZipArchive.read(ByteArrayImportSource("x.zip", zip)).archive.open(entry) }
    }

    @Test
    fun stopsAMemberThatInflatesPastTheCap() = runTest {
        val zip = zipOf("bomb.gpx" to ByteArray(20_000_000))
        assertTrue(zip.size < 100_000)
        val archive = ZipArchive.read(ByteArrayImportSource("x.zip", zip)).archive
        assertFailsWith<FileTooLargeException> { archive.open(archive.entries.single()).readAll(1_000_000) }
    }

    @Test
    fun listsNoMoreThanTheMemberLimit() = runTest {
        val zip = zipOf(*Array(5) { "f$it.gpx" to gpx })
        val listing = ZipArchive.read(ByteArrayImportSource("x.zip", zip), maxEntries = 3)
        assertEquals(3, listing.archive.entries.size)
        assertTrue(listing.truncated)
    }

    @Test
    fun failsOnSomethingThatIsNotAZip() = runTest {
        assertFailsWith<ActivityFileException> { ZipArchive.read(ByteArrayImportSource("x.zip", gpx)) }
        assertTrue(isZip(zipOf("a.gpx" to gpx)))
        assertFalse(isZip(gpx))
    }

    @Test
    fun streamsANestedZipWhoseSizesFollowItsData() = runTest {
        // ZipOutputStream writes each deflated member's sizes after its data, as a stream must.
        val inner = zipOf("1.fit" to fit, "notes.txt" to "hi".encodeToByteArray(), "2.gpx" to gpx, "3.fit" to fit, stored = setOf("3.fit"))
        val outer = zipOf("UploadedFiles_0-_Part1.zip" to inner)
        val archive = ZipArchive.read(ByteArrayImportSource("export.zip", outer)).archive
        val stream = ZipStream(archive.open(archive.entries.single()))
        val read = mutableListOf<Pair<String, ByteArray>>()
        var entry = stream.next()
        while (entry != null) {
            // Skip the text file unread, as a scan does.
            if (!entry.entry.name.endsWith(".txt")) read += entry.entry.path to entry.data.readAll(Int.MAX_VALUE)
            entry = stream.next()
        }
        assertEquals(listOf("1.fit", "2.gpx", "3.fit"), read.map { it.first })
        assertContentEquals(fit, read[0].second)
        assertContentEquals(gpx, read[1].second)
        assertContentEquals(fit, read[2].second)
    }

    @Test
    fun streamsAZipWithKnownSizes() = runTest {
        val zip = zipOf("a.gpx" to gpx, "b.fit" to fit, stored = setOf("a.gpx", "b.fit"))
        val stream = ZipStream(ChunkedInput(zip, chunk = 999))
        assertContentEquals(gpx, stream.next()!!.data.readAll(Int.MAX_VALUE))
        assertContentEquals(fit, stream.next()!!.data.readAll(Int.MAX_VALUE))
        assertEquals(null, stream.next())
    }

    @Test
    fun failsOnAMemberWhoseDataDoesNotMatchItsChecksum() = runTest {
        val zip = zipOf("b.fit" to fit, stored = setOf("b.fit")).flippedAt(fit.size / 2, after = fit.copyOfRange(0, 64))
        val archive = ZipArchive.read(ByteArrayImportSource("x.zip", zip)).archive
        assertFailsWith<ActivityFileException> { archive.open(archive.entries.single()).readAll(Int.MAX_VALUE) }
        assertFailsWith<ActivityFileException> { ZipStream(ChunkedInput(zip, chunk = 999)).next()!!.data.readAll(Int.MAX_VALUE) }
    }

    @Test
    fun failsOnAStreamedMemberWhoseDescriptorChecksumDiffers() = runTest {
        // The deflated member's CRC-32 is in the data descriptor after its data: flip its first byte.
        val zip = zipOf("a.gpx" to gpx).flippedAt(4, after = byteArrayOf(0x50, 0x4b, 0x07, 0x08))
        assertContentEquals(
            gpx,
            ZipArchive.read(ByteArrayImportSource("x.zip", zip)).archive.let { it.open(it.entries.single()) }.readAll(Int.MAX_VALUE),
        )
        assertFailsWith<ActivityFileException> { ZipStream(ChunkedInput(zip, chunk = 999)).next()!!.data.readAll(Int.MAX_VALUE) }
    }
}

/** A copy with the byte [offset] bytes past the first occurrence of [after] inverted. */
private fun ByteArray.flippedAt(offset: Int, after: ByteArray): ByteArray {
    val start = (0..size - after.size).first { at -> after.indices.all { this[at + it] == after[it] } }
    return copyOf().also { it[start + offset] = it[start + offset].toInt().inv().toByte() }
}

/** A zip of [files] in order, deflated unless named in [stored], with empty [folders] first. */
internal fun zipOf(
    vararg files: Pair<String, ByteArray>,
    stored: Set<String> = emptySet(),
    folders: List<String> = emptyList(),
): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        folders.forEach {
            zip.putNextEntry(JavaZipEntry(it))
            zip.closeEntry()
        }
        files.forEach { (path, bytes) ->
            val entry = JavaZipEntry(path)
            if (path in stored) {
                entry.method = JavaZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.compressedSize = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** A one-member stored zip with its method rewritten to [method] in both headers. */
private fun ByteArray.withMethod(method: Int): ByteArray {
    val copy = copyOf()
    for (at in 0 until size - 4) {
        val signature = u32(at)
        val methodAt =
            when (signature) {
                0x04034b50L -> at + 8
                0x02014b50L -> at + 10
                else -> continue
            }
        copy[methodAt] = method.toByte()
        copy[methodAt + 1] = 0
    }
    return copy
}
