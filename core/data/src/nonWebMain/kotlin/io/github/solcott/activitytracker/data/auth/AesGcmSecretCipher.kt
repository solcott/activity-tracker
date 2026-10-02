package io.github.solcott.activitytracker.data.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** AES-256-GCM over a raw key that [loadKey] fetches (or creates) from secure storage once, on first use. */
class AesGcmSecretCipher(private val loadKey: suspend () -> ByteArray) : SecretCipher {
    private val mutex = Mutex()
    private var key: AES.GCM.Key? = null

    private suspend fun key(): AES.GCM.Key = mutex.withLock {
        key ?: CryptographyProvider.Default.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, loadKey()).also { key = it }
    }

    // The cipher generates a random IV and prepends it to the output.
    override suspend fun encrypt(plaintext: String): String = Base64.encode(key().cipher().encrypt(plaintext.encodeToByteArray()))

    override suspend fun decrypt(ciphertext: String): String = key().cipher().decrypt(Base64.decode(ciphertext)).decodeToString()
}
