package io.github.solcott.activitytracker.atproto.crypto

/** Multicodec `p256-pub` (0x1200) as an unsigned varint. */
private val P256_PUB_MULTICODEC = byteArrayOf(0x80.toByte(), 0x24)

private const val UNCOMPRESSED_LENGTH = 65
private const val COORDINATE_LENGTH = 32
private const val UNCOMPRESSED_PREFIX: Byte = 0x04
private const val EVEN_Y_PREFIX: Byte = 0x02
private const val ODD_Y_PREFIX: Byte = 0x03

/**
 * `did:key` for a P-256 public key given as an uncompressed point (`0x04 || X || Y`): the compressed point behind the `p256-pub`
 * multicodec, base58btc with the multibase `z` prefix. atproto Spaces binds credentials to a key named this way.
 */
internal fun p256DidKey(uncompressed: ByteArray): String {
    require(uncompressed.size == UNCOMPRESSED_LENGTH && uncompressed[0] == UNCOMPRESSED_PREFIX) { "Not an uncompressed P-256 point" }
    val x = uncompressed.copyOfRange(1, 1 + COORDINATE_LENGTH)
    val yIsOdd = uncompressed.last().toInt() and 1 == 1
    val compressed = byteArrayOf(if (yIsOdd) ODD_Y_PREFIX else EVEN_Y_PREFIX) + x
    return "did:key:z" + (P256_PUB_MULTICODEC + compressed).base58Btc()
}

/**
 * The uncompressed point (`0x04 || X || Y`) of a P-256 key named as a `did:key` or a bare Multikey (`z…`, as a DID document's
 * `publicKeyMultibase` holds it). Throws [IllegalArgumentException] for anything else, a secp256k1 key included.
 */
internal fun p256PublicKey(multibaseOrDidKey: String): ByteArray {
    val multibase = multibaseOrDidKey.removePrefix("did:key:")
    require(multibase.startsWith("z")) { "Not a base58btc multibase key" }
    val bytes = base58BtcDecode(multibase.substring(1))
    require(bytes.size == P256_PUB_MULTICODEC.size + 1 + COORDINATE_LENGTH) { "Not a P-256 key" }
    require(bytes.copyOfRange(0, P256_PUB_MULTICODEC.size).contentEquals(P256_PUB_MULTICODEC)) { "Not a P-256 key" }
    return decompressP256(bytes.copyOfRange(P256_PUB_MULTICODEC.size, bytes.size))
}
