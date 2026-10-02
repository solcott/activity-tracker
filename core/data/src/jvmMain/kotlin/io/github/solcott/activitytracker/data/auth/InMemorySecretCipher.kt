package io.github.solcott.activitytracker.data.auth

import dev.whyoleg.cryptography.random.CryptographyRandom

/** JVM is only used for tests: a random key that lives as long as this instance. */
class InMemorySecretCipher : SecretCipher by AesGcmSecretCipher({ CryptographyRandom.nextBytes(KEY_BYTES) }) {
    private companion object {
        const val KEY_BYTES = 32
    }
}
