package io.github.solcott.activitytracker.data.importsave

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.vinceglb.filekit.PlatformFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

class BookmarkSourceKeeperIosTest {
    private val keeper = BookmarkSourceKeeper(Logger(loggerConfigInit(TestLogWriter(Severity.Verbose))))

    private fun tempFile(name: String, bytes: ByteArray): Path {
        val dir = Path(NSTemporaryDirectory(), NSUUID().UUIDString)
        SystemFileSystem.createDirectories(dir)
        val path = Path(dir, name)
        SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
        return path
    }

    @Test
    fun keepsAndReopensAFileInTheContainer() = runTest {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val path = tempFile("ride.fit", bytes)

        val kept = assertNotNull(keeper.keep("save", listOf(PickedFileSource(PlatformFile(path)))).single())
        assertEquals(KeptSource.Kind.BOOKMARK, kept.kind)

        val reopened = assertNotNull(keeper.open(assertNotNull(KeptSource.of(kept.kind.name, kept.data))))
        assertEquals(bytes.size.toLong(), reopened.source.size)
        assertContentEquals(bytes.copyOfRange(0, 4096), reopened.source.read(0, 4096))
        assertContentEquals(bytes.copyOfRange(150_000, 200_000), reopened.source.read(150_000, 100_000))
        reopened.source.release()
        reopened.close()
        SystemFileSystem.delete(path)
    }

    @Test
    fun aDeletedFileDoesNotReopen() = runTest {
        val path = tempFile("gone.gpx", "<gpx/>".encodeToByteArray())
        val kept = assertNotNull(keeper.keep("save", listOf(PickedFileSource(PlatformFile(path)))).single())
        SystemFileSystem.delete(path)
        assertNull(keeper.open(kept))
    }
}
