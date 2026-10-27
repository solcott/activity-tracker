package io.github.solcott.activitytracker.atproto.crypto

import io.github.solcott.activitytracker.lexicon.Cid

private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
private const val BASE32_BITS = 5
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
private const val CID_V1: Byte = 0x01

/**
 * The binary form of a CIDv1 in multibase `b` (lowercase base32, unpadded), as atproto writes CIDs. A Spaces record MAC covers these bytes.
 * Throws [IllegalArgumentException] for a CIDv0, another multibase, or bad base32.
 */
fun Cid.bytes(): ByteArray {
    require(value.startsWith("b")) { "Not a base32 CIDv1" }
    var buffer = 0
    var bits = 0
    val out = mutableListOf<Byte>()
    for (char in value.substring(1)) {
        val digit = BASE32_ALPHABET.indexOf(char)
        require(digit >= 0) { "Not a base32 CIDv1" }
        buffer = (buffer shl BASE32_BITS) or digit
        bits += BASE32_BITS
        if (bits >= BYTE_BITS) {
            bits -= BYTE_BITS
            out += (buffer shr bits and BYTE_MASK).toByte()
            buffer = buffer and (1 shl bits) - 1
        }
    }
    require(out.firstOrNull() == CID_V1) { "Not a CIDv1" }
    return out.toByteArray()
}
