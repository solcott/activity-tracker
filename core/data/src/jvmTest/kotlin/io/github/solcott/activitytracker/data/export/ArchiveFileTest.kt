package io.github.solcott.activitytracker.data.export

import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/** Writing an export's zip to a file a native UI names, which the iOS share sheet then offers (auth/data-export). */
class ArchiveFileTest {
    private val files = InMemoryExportFiles()
    private val archive = ExportArchive("e1", "export.zip", LocalDateTime(2026, 11, 5, 12, 0), ArchiveZipper(files))
    private val folder = createTempDirectory("archive").toFile()

    @Test
    fun theZipIsWrittenWhereAsked() = runTest {
        files.write("e1", DataExporter.README, "readme".encodeToByteArray())
        val target = File(folder, archive.name)

        assertTrue(archive.writeToPath(target.path))

        assertEquals(listOf(DataExporter.README), ZipFile(target).use { zip -> zip.entries().toList().map { it.name } })
    }

    @Test
    fun anEarlierFileIsReplaced() = runTest {
        files.write("e1", DataExporter.README, "readme".encodeToByteArray())
        val older = "an older export, much longer than the new one ".repeat(1000)
        val target = File(folder, archive.name).apply { writeText(older) }

        assertTrue(archive.writeToPath(target.path))

        assertTrue(target.length() < older.length, "not written over the old bytes")
        ZipFile(target).use { assertEquals(1, it.size()) }
    }

    @Test
    fun aFailedWriteLeavesNoFile() = runTest {
        val target = File(File(folder, "gone"), archive.name)

        assertFalse(archive.writeToPath(target.path))

        assertFalse(target.exists())
    }
}
