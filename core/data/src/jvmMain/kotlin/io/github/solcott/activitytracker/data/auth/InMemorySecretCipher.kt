package io.github.solcott.activitytracker.data.auth

import dev.whyoleg.cryptography.random.CryptographyRandom

/** For tests: a random key that lives as long as this instance. The desktop app uses [FileKeySecretCipher]. */
class InMemorySecretCipher : SecretCipher by AesGcmSecretCipher({ CryptographyRandom.nextBytes(KEY_BYTES) }) {
    private companion object {
        const val KEY_BYTES = 32
    }
}
