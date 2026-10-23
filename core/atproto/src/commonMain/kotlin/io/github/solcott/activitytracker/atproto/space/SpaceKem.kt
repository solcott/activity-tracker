package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.atproto.crypto.p256PublicKey

private const val SECRET_LENGTH = 32

/** RFC 9180's labels; `0x0010` names DHKEM(P-256, HKDF-SHA256). */
private val KEM_PREFIX = "HPKE-v1".encodeToByteArray() + "KEM".encodeToByteArray() + uint16be(0x0010)

/**
 * One request's key agreement with a Spaces author's `#atproto_kem` key, as `@atproto/space`'s `encapsulate` does it: send [keyShare] in
 * `Atproto-Space-Want-Mac`, and check the response's MAC with [sharedSecret]. Make a new one for every request, retries and pages included;
 * [SpaceMac] does, which is why nothing takes an ephemeral key from outside.
 */
internal class SpaceKem private constructor(val keyShare: String, val sharedSecret: ByteArray) {
    companion object {
        private val ecdh
            get() = CryptographyProvider.Default.get(ECDH)

        /** A fresh ephemeral P-256 key agreed against [authorKem], a P-256 Multikey or `did:key`. */
        suspend fun encapsulate(authorKem: String): SpaceKem {
            val ephemeral = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
            return encapsulate(authorKem, ephemeral.privateKey, ephemeral.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW))
        }

        /** [encapsulate] with a given ephemeral key, for vectors. */
        internal suspend fun encapsulate(authorKem: String, ephemeral: ECDH.PrivateKey, ephemeralRaw: ByteArray): SpaceKem {
            val authorRaw = p256PublicKey(authorKem)
            val author = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PublicKey.Format.RAW, authorRaw)
            val dh = ephemeral.sharedSecretGenerator().generateSharedSecretToByteArray(author)
            return SpaceKem(p256DidKey(ephemeralRaw), derive(dh, ephemeralRaw, authorRaw))
        }

        /**
         * The author's side ([SpaceMacHost]): the secret a reader agreed by sending [keyShare] against the author's KEM key
         * ([authorPrivate], whose public point is [authorRaw]).
         */
        internal suspend fun decapsulate(authorPrivate: ECDH.PrivateKey, authorRaw: ByteArray, keyShare: String): ByteArray {
            val ephemeralRaw = p256PublicKey(keyShare)
            val ephemeral = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PublicKey.Format.RAW, ephemeralRaw)
            val dh = authorPrivate.sharedSecretGenerator().generateSharedSecretToByteArray(ephemeral)
            return derive(dh, ephemeralRaw, authorRaw)
        }

        /** HKDF-SHA256 over the DH x-coordinate, with both public points (reader's first) in `info`. */
        private suspend fun derive(dh: ByteArray, ephemeralRaw: ByteArray, authorRaw: ByteArray): ByteArray {
            val ikm = KEM_PREFIX + "eae_prk".encodeToByteArray() + dh
            val info = uint16be(SECRET_LENGTH) + KEM_PREFIX + "shared_secret".encodeToByteArray() + ephemeralRaw + authorRaw
            return CryptographyProvider.Default.get(HKDF)
                .secretDerivation(SHA256, SECRET_LENGTH.bytes, salt = ByteArray(0), info = info)
                .deriveSecretToByteArray(ikm)
        }
    }
}

private const val BYTE_BITS = 8
private const val UINT16_MAX = 0xFFFF

internal fun uint16be(value: Int): ByteArray {
    require(value in 0..UINT16_MAX) { "Doesn't fit in a uint16: $value" }
    return byteArrayOf((value shr BYTE_BITS).toByte(), value.toByte())
}
