package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.data.di.DesktopAppDirs
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalKermitApi::class)
class DesktopSecretCipherTest {
    private val dirs = DesktopAppDirs(createTempDirectory("desktop-secret"))
    private val keyFile = dirs.data.resolve("secret.key")
    private val logs = TestLogWriter(Severity.Verbose)

    /** An OS credential store that's [available] (else there's none, as on Linux) and [refuses] writes when set. */
    private class FakeKeyStore(var value: String? = null, var available: Boolean = true, var refuses: Boolean = false) : DesktopKeyStore {
        override fun read(): String? = value.takeIf { available }

        override fun write(value: String) {
            if (!available) throw KeyStoreRefusedException("No credential store")
            if (refuses) throw KeyStoreRefusedException("The credential store refused the key")
            this.value = value
        }
    }

    private fun cipher(store: DesktopKeyStore) =
        DesktopSecretCipher(dirs, store, Logger(loggerConfigInit(logs)), UnconfinedTestDispatcher())

    @Test
    fun firstLaunchKeepsTheKeyOnlyInTheStore() = runTest {
        val store = FakeKeyStore()
        cipher(store).encrypt("token")

        assertEquals(32, Base64.decode(assertNotNull(store.value)).size)
        assertFalse(keyFile.exists())
        logs.assertCount(0)
    }

    @Test
    fun aRestartReadsTheSameKeyFromTheStore() = runTest {
        val store = FakeKeyStore()
        val sealed = cipher(store).encrypt("token")

        assertEquals("token", cipher(store).decrypt(sealed))
        assertFalse(keyFile.exists())
    }

    @Test
    fun aKeyFileMovesIntoTheStoreAndKeepsTheSession() = runTest {
        // An earlier build kept its key in the file.
        val store = FakeKeyStore(available = false)
        val sealed = cipher(store).encrypt("token")
        val fileKey = keyFile.readBytes()

        store.available = true
        assertEquals("token", cipher(store).decrypt(sealed))
        assertEquals(Base64.encode(fileKey), store.value)
        assertFalse(keyFile.exists())
    }

    @Test
    fun aKeyFileBeatsAnOlderKeyInTheStore() = runTest {
        // The store refused a newer key once, so the file holds the one the session was sealed with.
        val sealed = cipher(FakeKeyStore(available = false)).encrypt("token")
        val fileKey = keyFile.readBytes()
        val store = FakeKeyStore(value = Base64.encode(ByteArray(32) { 1 }))

        assertEquals("token", cipher(store).decrypt(sealed))
        assertEquals(Base64.encode(fileKey), store.value)
        assertFalse(keyFile.exists())
    }

    @Test
    fun aRefusedMigrationKeepsTheFile() = runTest {
        val sealed = cipher(FakeKeyStore(available = false)).encrypt("token")
        val store = FakeKeyStore(refuses = true)

        assertEquals("token", cipher(store).decrypt(sealed))
        assertTrue(keyFile.exists())
        assertNull(store.value)
        logs.assertLast { severity == Severity.Warn && "refused" in message }
    }

    @Test
    fun noStoreFallsBackToAnOwnerOnlyFileAndWarnsWithoutTheKey() = runTest {
        val sealed = cipher(FakeKeyStore(available = false)).encrypt("token")

        assertEquals("token", cipher(FakeKeyStore(available = false)).decrypt(sealed))
        val key = keyFile.readBytes()
        assertEquals(32, key.size)
        if ("posix" in keyFile.fileSystem.supportedFileAttributeViews()) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile)))
        }
        assertTrue(logs.logs.isNotEmpty())
        assertTrue(logs.logs.all { it.severity == Severity.Warn && Base64.encode(key) !in it.message })
    }

    @Test
    fun aRefusedWriteFallsBackToTheFile() = runTest {
        val store = FakeKeyStore(refuses = true)
        val sealed = cipher(store).encrypt("token")

        assertNull(store.value)
        assertTrue(keyFile.exists())
        assertEquals("token", cipher(store).decrypt(sealed))
    }

    @Test
    fun aStoredValueThatIsNoKeyIsReplaced() = runTest {
        val store = FakeKeyStore(value = "not a key")
        val sealed = cipher(store).encrypt("token")

        assertEquals(32, Base64.decode(assertNotNull(store.value)).size)
        assertEquals("token", cipher(store).decrypt(sealed))
    }
}
