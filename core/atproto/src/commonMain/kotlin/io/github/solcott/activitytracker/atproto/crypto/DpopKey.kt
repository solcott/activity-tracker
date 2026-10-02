package io.github.solcott.activitytracker.atproto.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.solcott.activitytracker.lexicon.AtJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ES256 (P-256) key used to bind OAuth tokens via DPoP. It is generated per sign-in and persisted with the session ([Serialized]) so tokens
 * stay usable across app restarts and web redirects.
 */
class DpopKey
private constructor(
    private val privateKey: ECDSA.PrivateKey,
    private val publicRaw: ByteArray,
) {
    /** Public key as a JWK, embedded in every DPoP proof header. */
    val publicJwk: JsonObject = buildJsonObject {
        // RAW uncompressed = 0x04 || X(32) || Y(32)
        put("kty", "EC")
        put("crv", "P-256")
        put("x", publicRaw.copyOfRange(1, 33).base64Url())
        put("y", publicRaw.copyOfRange(33, 65).base64Url())
    }

    suspend fun sign(data: ByteArray): ByteArray = privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW).generateSignature(data)

    suspend fun serialize(): Serialized =
        Serialized(
            privateKeyDer = privateKey.encodeToByteArray(EC.PrivateKey.Format.DER).base64Url(),
            publicKeyRaw = publicRaw.base64Url(),
        )

    @Serializable
    data class Serialized(val privateKeyDer: String, val publicKeyRaw: String) {
        fun encode(): String = AtJson.encodeToString(serializer(), this)

        companion object {
            fun decode(value: String): Serialized = AtJson.decodeFromString(serializer(), value)
        }
    }

    companion object {
        private val ecdsa
            get() = CryptographyProvider.Default.get(ECDSA)

        suspend fun generate(): DpopKey {
            val pair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
            return DpopKey(
                pair.privateKey,
                pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW),
            )
        }

        suspend fun restore(serialized: Serialized): DpopKey {
            val privateKey =
                ecdsa
                    .privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(
                        EC.PrivateKey.Format.DER,
                        Base64Url.decode(serialized.privateKeyDer),
                    )
            return DpopKey(privateKey, Base64Url.decode(serialized.publicKeyRaw))
        }
    }
}
