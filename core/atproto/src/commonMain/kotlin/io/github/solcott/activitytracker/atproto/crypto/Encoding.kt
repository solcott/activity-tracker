package io.github.solcott.activitytracker.atproto.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64

internal val Base64Url: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun ByteArray.base64Url(): String = Base64Url.encode(this)

internal suspend fun sha256(bytes: ByteArray): ByteArray = CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)

/** URL-safe random token with [bytes] bytes of entropy. */
internal fun randomToken(bytes: Int = 32): String = CryptographyRandom.nextBytes(bytes).base64Url()
