package io.github.solcott.activitytracker.app.ui

import coil3.PlatformContext
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class RouteImageStoreTest {
    private val directory = createTempDirectory("route-maps").toOkioPath()
    private val store = RouteImageStore(directory)
    private val disk = requireNotNull(store.loader(PlatformContext.INSTANCE).diskCache)

    @AfterTest fun deleteDirectory() = FileSystem.SYSTEM.deleteRecursively(directory)

    private fun write(key: String) {
        val editor = requireNotNull(disk.openEditor(key))
        FileSystem.SYSTEM.write(editor.data) { writeUtf8("png") }
        editor.commit()
    }

    private fun stored(key: String) = disk.openSnapshot(key)?.also { it.close() }

    @Test
    fun signingOutDropsStoredImages() = runTest {
        store.onSession("did:plc:alice")
        write("followers-only-ride")
        assertNotNull(stored("followers-only-ride"))

        store.onSession(null)

        assertNull(stored("followers-only-ride"))
    }

    @Test
    fun switchingAccountsDropsStoredImages() = runTest {
        store.onSession("did:plc:alice")
        write("ride")

        store.onSession("did:plc:bob")

        assertNull(stored("ride"))
    }

    @Test
    fun startingSignedInKeepsStoredImages() = runTest {
        write("ride")

        store.onSession("did:plc:alice")

        assertNotNull(stored("ride"))
    }

    @Test
    fun startingSignedOutDropsImagesBeforeTheLoaderIsBuilt() = runTest {
        write("ride")
        val fresh = RouteImageStore(directory)

        fresh.onSession(null)

        assertNull(fresh.loader(PlatformContext.INSTANCE).diskCache?.openSnapshot("ride"))
    }
}
