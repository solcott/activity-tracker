package io.github.solcott.activitytracker.data.importsave

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.vinceglb.filekit.PlatformFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class BookmarkSourceKeeperTest {
    private val dir = Files.createTempDirectory("kept-sources").toFile()
    private val keeper = BookmarkSourceKeeper(Logger(loggerConfigInit(TestLogWriter(Severity.Verbose))))

    @AfterTest fun cleanUp() = dir.deleteRecursively().let {}

    @Test
    fun keepsAndReopensAPickedFile() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val file = dir.resolve("ride.fit").apply { writeBytes(bytes) }

        val kept = keeper.keep("save", listOf(PickedFileSource(PlatformFile(file)))).single()
        assertEquals(KeptSource.Kind.BOOKMARK, assertNotNull(kept).kind)

        // As a later process would: only the stored form.
        val reopened = assertNotNull(keeper.open(assertNotNull(KeptSource.of(kept.kind.name, kept.data))))
        assertEquals("ride.fit", reopened.source.name)
        assertEquals(bytes.size.toLong(), reopened.source.size)
        assertContentEquals(bytes.copyOfRange(0, 1000), reopened.source.read(0, 1000))
        assertContentEquals(bytes.copyOfRange(250_000, 300_000), reopened.source.read(250_000, 100_000))
        reopened.source.release()
        reopened.close()
        keeper.release("save", listOf(kept))
    }

    @Test
    fun aDeletedFileDoesNotReopen() = runTest {
        val file = dir.resolve("gone.gpx").apply { writeText("<gpx/>") }
        val kept = assertNotNull(keeper.keep("save", listOf(PickedFileSource(PlatformFile(file)))).single())
        file.delete()
        assertNull(keeper.open(kept))
    }

    @Test
    fun aSourceWithoutAFileIsNotKept() = runTest {
        assertEquals(listOf<KeptSource?>(null), keeper.keep("save", listOf(ByteArrayImportSource("a.fit", byteArrayOf(1)))))
    }
}
