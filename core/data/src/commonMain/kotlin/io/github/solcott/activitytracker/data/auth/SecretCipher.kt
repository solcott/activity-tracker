package io.github.solcott.activitytracker.data.auth

/**
 * Encrypts small secrets at rest with an AES-256-GCM key held by platform secure storage: Android Keystore, the iOS Keychain, or a
 * non-extractable WebCrypto key in IndexedDB.
 */
interface SecretCipher {
    /** Returns base64(iv || ciphertext || tag). */
    suspend fun encrypt(plaintext: String): String

    /** Throws if the key is gone or [ciphertext] was not produced by this cipher. */
    suspend fun decrypt(ciphertext: String): String
}
