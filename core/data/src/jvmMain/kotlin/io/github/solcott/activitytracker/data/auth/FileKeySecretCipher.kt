package io.github.solcott.activitytracker.data.auth

import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.DesktopAppDirs
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop: AES-256-GCM with a key kept in an owner-only file in the app data dir. Development-grade next to the Android Keystore or iOS
 * Keychain, since anything running as the user can read it; swap in the OS keychain before shipping desktop.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class FileKeySecretCipher(dirs: DesktopAppDirs) :
    SecretCipher by AesGcmSecretCipher({ withContext(Dispatchers.IO) { loadOrCreateKey(dirs.data.resolve(KEY_FILE)) } }) {
    private companion object {
        const val KEY_FILE = "secret.key"
        const val KEY_BYTES = 32

        fun loadOrCreateKey(file: Path): ByteArray {
            if (file.exists()) return file.readBytes()
            val key = CryptographyRandom.nextBytes(KEY_BYTES)
            val ownerOnly =
                if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
                    arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                } else {
                    emptyArray()
                }
            Files.write(Files.createFile(file, *ownerOnly), key)
            return key
        }
    }
}
