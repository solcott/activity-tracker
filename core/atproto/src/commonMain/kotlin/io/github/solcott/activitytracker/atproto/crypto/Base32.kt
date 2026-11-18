package io.github.solcott.activitytracker.atproto.crypto

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
private const val BITS_PER_CHAR = 5
private const val BYTE_MASK = 0xFF
private const val CHAR_MASK = 0x1F
private const val CHARS_PER_BLOCK = 8

/** Tail lengths, past a whole 8-character block, that no whole number of bytes encodes to. */
private val TRUNCATED_TAILS = setOf(1, 3, 6)

/** RFC 4648 base32, lowercase and without padding (multibase `b`), as atproto writes CIDs. */
fun ByteArray.base32(): String = buildString {
    var buffer = 0
    var bits = 0
    for (byte in this@base32) {
        buffer = (buffer shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
        bits += Byte.SIZE_BITS
        while (bits >= BITS_PER_CHAR) {
            bits -= BITS_PER_CHAR
            append(ALPHABET[(buffer shr bits) and CHAR_MASK])
        }
    }
    if (bits > 0) append(ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
}

/**
 * Inverse of [base32], dropping the bits left over past the last whole byte. Throws [IllegalArgumentException] outside the alphabet, or for
 * a truncated length no byte count encodes to (1, 3 or 6 characters past a multiple of 8).
 */
fun base32Decode(value: String): ByteArray {
    require(value.length % CHARS_PER_BLOCK !in TRUNCATED_TAILS) { "Truncated base32" }
    var buffer = 0
    var bits = 0
    val out = mutableListOf<Byte>()
    for (char in value) {
        val digit = ALPHABET.indexOf(char)
        require(digit >= 0) { "Not base32" }
        buffer = (buffer shl BITS_PER_CHAR) or digit
        bits += BITS_PER_CHAR
        if (bits >= Byte.SIZE_BITS) {
            bits -= Byte.SIZE_BITS
            out += (buffer shr bits and BYTE_MASK).toByte()
            buffer = buffer and (1 shl bits) - 1
        }
    }
    return out.toByteArray()
}
