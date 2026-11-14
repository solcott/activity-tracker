package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.atproto.crypto.p256PublicKey

private const val SECRET_LENGTH = 32

/** HKDF-SHA256's output length, and the zero salt RFC 5869 uses for an empty one. */
private const val HASH_LENGTH = 32

private val HPKE_VERSION = "HPKE-v1".encodeToByteArray()

/** `0x0010` is DHKEM(P-256, HKDF-SHA256), `0x0001` HKDF-SHA256 and `0xFFFF` the Export-only AEAD. */
private val KEM_ID = "KEM".encodeToByteArray() + uint16be(0x0010)
private val SUITE_ID = "HPKE".encodeToByteArray() + uint16be(0x0010) + uint16be(0x0001) + uint16be(0xFFFF)

/** A fixed label tying the secret to this protocol: HPKE's `info`. */
private val HPKE_INFO = "atproto-mac".encodeToByteArray()
private val MODE_BASE = byteArrayOf(0x00)
private val EMPTY = ByteArray(0)

/**
 * One request's key agreement with a Spaces author's `#atproto_kem` key, as `@atproto/space`'s `encapsulate` does it: HPKE secret export
 * (RFC 9180 §6.2) in base mode, with `info` `atproto-mac` and an empty exporter context. Send [keyShare] (HPKE's `enc`, as a compressed
 * `did:key`) as the `keyid` of the MAC request, and check the response's MAC with [sharedSecret]. Make a new one for every request, retries
 * and pages included; [SpaceMac] does, which is why nothing takes an ephemeral key from outside.
 *
 * No Kotlin Multiplatform library offers HPKE, so this spells out its ECDH and HKDF steps for that one ciphersuite, as upstream does,
 * checked against a vector computed with Go's `crypto/hpke`.
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

        /** HPKE's SetupBaseS (or SetupBaseR) from the ECDH x-coordinate [dh], then an export with an empty exporter context. */
        private suspend fun derive(dh: ByteArray, enc: ByteArray, authorRaw: ByteArray): ByteArray {
            // The KEM's secret, from the ECDH result and both public points (uncompressed, the reader's first).
            val eaePrk = labeledExtract(KEM_ID, EMPTY, "eae_prk", dh)
            val kemSecret = labeledExpand(KEM_ID, eaePrk, "shared_secret", enc + authorRaw, SECRET_LENGTH)
            // The key schedule, in base mode with no pre-shared key.
            val pskIdHash = labeledExtract(SUITE_ID, EMPTY, "psk_id_hash", EMPTY)
            val infoHash = labeledExtract(SUITE_ID, EMPTY, "info_hash", HPKE_INFO)
            val context = MODE_BASE + pskIdHash + infoHash
            val secret = labeledExtract(SUITE_ID, kemSecret, "secret", EMPTY)
            val exporterSecret = labeledExpand(SUITE_ID, secret, "exp", context, HASH_LENGTH)
            return labeledExpand(SUITE_ID, exporterSecret, "sec", EMPTY, SECRET_LENGTH)
        }

        private suspend fun labeledExtract(id: ByteArray, salt: ByteArray, label: String, ikm: ByteArray): ByteArray =
            extract(salt, HPKE_VERSION + id + label.encodeToByteArray() + ikm)

        private suspend fun labeledExpand(id: ByteArray, prk: ByteArray, label: String, info: ByteArray, length: Int): ByteArray =
            expand(prk, uint16be(length) + HPKE_VERSION + id + label.encodeToByteArray() + info, length)

        /** HKDF-Extract (RFC 5869 §2.2): an empty salt is HashLen zeros, which HMAC treats the same as no key. */
        private suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray =
            SpaceMac.hmacSha256(salt.takeIf { it.isNotEmpty() } ?: ByteArray(HASH_LENGTH), ikm)

        /** HKDF-Expand (RFC 5869 §2.3) for at most one block, all HPKE asks of it here. */
        private suspend fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
            require(length in 1..HASH_LENGTH) { "One HKDF block only" }
            return SpaceMac.hmacSha256(prk, info + byteArrayOf(1)).copyOf(length)
        }
    }
}

private const val BYTE_BITS = 8
private const val UINT16_MAX = 0xFFFF

internal fun uint16be(value: Int): ByteArray {
    require(value in 0..UINT16_MAX) { "Doesn't fit in a uint16: $value" }
    return byteArrayOf((value shr BYTE_BITS).toByte(), value.toByte())
}
