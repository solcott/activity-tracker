package io.github.solcott.activitytracker.atproto.oauth

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.base64Url
import io.github.solcott.activitytracker.atproto.crypto.randomToken
import io.github.solcott.activitytracker.atproto.crypto.sha256
import io.github.solcott.activitytracker.atproto.crypto.signJwt
import kotlin.time.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Creates DPoP proof JWTs (RFC 9449). */
internal class DpopProofs(private val clock: Clock) {
    suspend fun create(
        key: DpopKey,
        method: String,
        url: String,
        nonce: String?,
        accessToken: String? = null,
    ): String {
        val header = buildJsonObject {
            put("typ", "dpop+jwt")
            put("alg", "ES256")
            put("jwk", key.publicJwk)
        }
        val payload = buildJsonObject {
            put("jti", randomToken(16))
            put("htm", method.uppercase())
            put("htu", url.substringBefore('?').substringBefore('#'))
            put("iat", clock.now().epochSeconds)
            nonce?.let { put("nonce", it) }
            accessToken?.let { put("ath", sha256(it.encodeToByteArray()).base64Url()) }
        }
        return signJwt(header, payload, key)
    }
}
