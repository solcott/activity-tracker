package io.github.solcott.activitytracker.appview.auth

import java.math.BigInteger
import java.security.MessageDigest
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner

/** A repo signing key decoded from a DID document's `publicKeyMultibase`. */
data class SigningKey(val curve: Curve, val params: ECPublicKeyParameters) {
    /** Whether [signature], a compact (r || s) ECDSA signature, signs the SHA-256 of [data] with this key. */
    fun verifies(data: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != SIGNATURE_BYTES) return false
        val r = BigInteger(1, signature.copyOfRange(0, SIGNATURE_BYTES / 2))
        val s = BigInteger(1, signature.copyOfRange(SIGNATURE_BYTES / 2, SIGNATURE_BYTES))
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return ECDSASigner().apply { init(false, params) }.verifySignature(digest, r, s)
    }

    enum class Curve(val jwtAlg: String, val bcName: String) {
        K256("ES256K", "secp256k1"),
        P256("ES256", "secp256r1"),
    }

    private companion object {
        /** Two 32-byte integers. */
        const val SIGNATURE_BYTES = 64
    }
}

object Multikey {
    private val K256_PREFIX = byteArrayOf(0xe7.toByte(), 0x01)
    private val P256_PREFIX = byteArrayOf(0x80.toByte(), 0x24)

    fun decode(multibase: String): SigningKey {
        require(multibase.startsWith('z')) { "Only base58btc multibase keys are supported" }
        val bytes = Base58.decode(multibase.substring(1))
        val (curve, prefix) =
            when {
                bytes.startsWith(K256_PREFIX) -> SigningKey.Curve.K256 to K256_PREFIX
                bytes.startsWith(P256_PREFIX) -> SigningKey.Curve.P256 to P256_PREFIX
                else -> throw IllegalArgumentException("Unsupported multikey codec")
            }
        val domain = domain(curve)
        val point = domain.curve.decodePoint(bytes.copyOfRange(prefix.size, bytes.size))
        return SigningKey(curve, ECPublicKeyParameters(point, domain))
    }

    fun domain(curve: SigningKey.Curve): ECDomainParameters {
        val x9: X9ECParameters = CustomNamedCurves.getByName(curve.bcName)
        return ECDomainParameters(x9.curve, x9.g, x9.n, x9.h)
    }

    /** Encodes a compressed public key as a multibase multikey (used by tests). */
    fun encode(curve: SigningKey.Curve, compressedPoint: ByteArray): String {
        val prefix = if (curve == SigningKey.Curve.K256) K256_PREFIX else P256_PREFIX
        return "z" + Base58.encode(prefix + compressedPoint)
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size > prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    fun decode(input: String): ByteArray {
        var value = BigInteger.ZERO
        for (c in input) {
            val digit = ALPHABET.indexOf(c)
            require(digit >= 0) { "Invalid base58 character '$c'" }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes =
            value.toByteArray().let {
                if (it.size > 1 && it[0] == 0.toByte()) {
                    it.copyOfRange(1, it.size)
                } else {
                    it
                }
            }
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + if (value == BigInteger.ZERO) ByteArray(0) else bytes
    }

    fun encode(input: ByteArray): String {
        var value = BigInteger(1, input)
        val sb = StringBuilder()
        while (value > BigInteger.ZERO) {
            val (q, r) = value.divideAndRemainder(BASE)
            sb.append(ALPHABET[r.toInt()])
            value = q
        }
        repeat(input.takeWhile { it == 0.toByte() }.size) { sb.append('1') }
        return sb.reverse().toString()
    }
}
