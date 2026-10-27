package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.DesktopAppDirs
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.encoding.Base64
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop: AES-256-GCM with a key kept in the OS credential store ([DesktopKeyStore]). A `secret.key` file in the app data dir, left by an
 * earlier build or a past fallback, is the newest key: it's moved into the store and deleted. Where there's no store (Linux) or it refuses
 * the key, the key stays in that owner-only file, which anything running as the user can read, and a warning is logged. The file is written
 * whole or not at all, and one that holds no key is deleted, never used or moved into the store.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopSecretCipher(
    dirs: DesktopAppDirs,
    store: DesktopKeyStore,
    logger: Logger,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) :
    SecretCipher by AesGcmSecretCipher({
        withContext(ioDispatcher) { loadOrCreateKey(dirs.data.resolve(KEY_FILE), store, logger.withTag("DesktopSecretCipher")) }
    })

private const val KEY_FILE = "secret.key"
private const val KEY_BYTES = 32

private fun loadOrCreateKey(file: Path, store: DesktopKeyStore, log: Logger): ByteArray {
    if (file.exists()) {
        val key = file.readBytes()
        if (key.size == KEY_BYTES) return key.also { store.save(it, log, saved = { file.deleteIfExists() }, refused = {}) }
        // Left by a crash in an older build's two-step write: never a key, and never moved into the store.
        log.w { "Replacing $KEY_FILE, which holds no key" }
        file.deleteIfExists()
    }
    return store.read()?.let(::decodeKey)
        ?: CryptographyRandom.nextBytes(KEY_BYTES).also { key -> store.save(key, log, saved = {}, refused = { writeOwnerOnly(file, key) }) }
}

/** Saves [key] in the store and runs [saved], or, when the store refuses it, warns (never with the key) and runs [refused]. */
private fun DesktopKeyStore.save(key: ByteArray, log: Logger, saved: () -> Unit, refused: () -> Unit) {
    try {
        write(Base64.encode(key))
    } catch (e: KeyStoreRefusedException) {
        log.w { "Keeping the secret key in $KEY_FILE: ${e.message}" }
        return refused()
    }
    saved()
}

/** A stored value that isn't a key reads as none, so a new key replaces it and stored state reads as signed out. */
private fun decodeKey(stored: String): ByteArray? = runCatching { Base64.decode(stored) }.getOrNull()?.takeIf { it.size == KEY_BYTES }

/** Writes [key] to a temporary file and renames it onto [file], so [file] is never there without the whole key. */
private fun writeOwnerOnly(file: Path, key: ByteArray) {
    val temp = file.resolveSibling("$KEY_FILE.tmp")
    temp.deleteIfExists()
    val created =
        if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
            Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            Files.createFile(temp)
        }
    FileChannel.open(created, StandardOpenOption.WRITE).use { channel ->
        channel.write(ByteBuffer.wrap(key))
        channel.force(true)
    }
    Files.move(created, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
