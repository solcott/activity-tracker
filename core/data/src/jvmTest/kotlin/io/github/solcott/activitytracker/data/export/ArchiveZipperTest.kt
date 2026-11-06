package io.github.solcott.activitytracker.data.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/** The export's zip, read back with `java.util.zip` (auth/data-export). */
class ArchiveZipperTest {
    private val files = InMemoryExportFiles()
    private val at = LocalDateTime(2026, 11, 5, 17, 10, 46)

    private suspend fun zipped(id: String): ByteArray {
        val out = ByteArrayOutputStream()
        var chunks = 0
        ArchiveZipper(files).zip(id, at) {
            out.write(it)
            chunks++
        }
        assertTrue(chunks > 1, "written as it goes, not at the end")
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): List<Pair<ZipEntry, ByteArray>> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry }.map { it to zip.readBytes() }.toList() }

    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gz -> gz.write(bytes) } }.toByteArray()

    @Test
    fun everyStagedFileComesBackExceptOriginalsBlobs() = runTest {
        val stream = gzip("streams".repeat(1000).encodeToByteArray())
        files.write("e1", DataExporter.README, "readme".encodeToByteArray())
        files.write(
            "e1",
            DataExporter.MANIFEST,
            """{"did":"did:plc:alice","handle":"alice.test","pds":"pds.test","exportedAt":"x","spacesIncluded":true,"unsentChanges":0,
              "counts":{"records":1,"blobs":2,"activityFiles":1},"activities":{},"originals":{"bafysrc":"activities/a.fit"},"problems":[]}"""
                .encodeToByteArray(),
        )
        files.write("e1", "activities/a.fit", ByteArray(3000) { (it % 7).toByte() })
        files.write("e1", "records/repo/app.activitytracker.feed.activity/3k.json", """{"a":1}""".encodeToByteArray())
        files.write("e1", "blobs/self/bafystreams", stream)
        files.write("e1", "blobs/self/bafysrc", gzip("original".encodeToByteArray()))
        // Big enough to be written in several chunks.
        files.write("e1", "blobs/repo/bafybig", ByteArray(3 * 1024 * 1024) { (it % 251).toByte() })

        val entries = unzip(zipped("e1"))

        assertEquals(
            listOf(
                DataExporter.README,
                DataExporter.MANIFEST,
                "activities/a.fit",
                "records/repo/app.activitytracker.feed.activity/3k.json",
                "blobs/repo/bafybig",
                "blobs/self/bafystreams",
            ),
            entries.map { it.first.name },
        )
        entries.forEach { (entry, bytes) -> assertContentEquals(files.read("e1", entry.name), bytes, entry.name) }
        assertEquals(ZipEntry.STORED, entries.single { it.first.name == "blobs/self/bafystreams" }.first.method, "gzip is stored as is")
        assertEquals(ZipEntry.DEFLATED, entries.single { it.first.name == DataExporter.README }.first.method)
    }

    @Test
    fun entriesAreDatedWhenTheExportRan() = runTest {
        files.write("e1", DataExporter.README, "readme".encodeToByteArray())
        files.write("e1", DataExporter.recordPath(DataExporter.REPO, "app.activitytracker.feed.activity", "r1"), "{}".encodeToByteArray())
        // DOS times hold even seconds only.
        val out = ByteArrayOutputStream()
        ArchiveZipper(files).zip("e1", at) { out.write(it) }
        unzip(out.toByteArray()).forEach { (entry, _) ->
            assertEquals(java.time.LocalDateTime.of(2026, 11, 5, 17, 10, 46), entry.timeLocal)
        }
    }

    @Test
    fun moreThan65535EntriesUseZip64() = runTest {
        repeat(70_000) { files.write("e2", "records/repo/c/$it.json", "{}".encodeToByteArray()) }

        val entries = unzip(zipped("e2"))

        assertEquals(70_000, entries.size)
    }
}
