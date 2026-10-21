package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.Multikey
import io.github.solcott.activitytracker.appview.auth.SigningKey
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator

/** A secp256k1 account key, like a PDS-hosted repo's, that can mint service-auth JWTs. */
class TestAccountKey {
    private val domain = Multikey.domain(SigningKey.Curve.K256)
    private val pair = ECKeyPairGenerator().apply { init(ECKeyGenerationParameters(domain, SecureRandom())) }.generateKeyPair()

    val multibase: String =
        Multikey.encode(
            SigningKey.Curve.K256,
            (pair.public as ECPublicKeyParameters).q.getEncoded(true),
        )

    fun serviceJwt(iss: String, aud: String, lxm: String?, expSeconds: Long = 60): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = buildJsonObject {
            put("typ", "JWT")
            put("alg", "ES256K")
        }
        val payload = buildJsonObject {
            put("iss", iss)
            put("aud", aud)
            put("exp", (Clock.System.now() + expSeconds.seconds).epochSeconds)
            lxm?.let { put("lxm", it) }
        }
        val input = enc.encodeToString(header.toString().toByteArray()) + "." + enc.encodeToString(payload.toString().toByteArray())
        return "$input.${enc.encodeToString(sign(input.toByteArray()))}"
    }

    /** A compact low-S ECDSA signature of the SHA-256 of [data], as atproto signs JWTs and labels. */
    fun sign(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest())).apply { init(true, pair.private as ECPrivateKeyParameters) }
        val (r, s) = signer.generateSignature(digest).let { it[0] to it[1] }
        val lowS = if (s > domain.n.shiftRight(1)) domain.n - s else s
        return r.to32() + lowS.to32()
    }

    private fun BigInteger.to32(): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }
}
