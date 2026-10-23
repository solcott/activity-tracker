package io.github.solcott.activitytracker.atproto.crypto

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private const val BASE = 58
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8

/** Bitcoin-alphabet base58 (multibase `z`), as `did:key` uses. Each leading zero byte becomes a leading `1`. */
internal fun ByteArray.base58Btc(): String {
    val zeros = takeWhile { it.toInt() == 0 }.size
    // Little-endian base-58 digits of the big-endian number in this array.
    val digits = mutableListOf<Int>()
    for (byte in drop(zeros)) {
        var carry = byte.toInt() and BYTE_MASK
        for (i in digits.indices) {
            carry += digits[i] shl BYTE_BITS
            digits[i] = carry % BASE
            carry /= BASE
        }
        while (carry > 0) {
            digits += carry % BASE
            carry /= BASE
        }
    }
    return "1".repeat(zeros) + digits.asReversed().joinToString("") { ALPHABET[it].toString() }
}

/** Inverse of [base58Btc]. Throws [IllegalArgumentException] on a character outside the alphabet. */
internal fun base58BtcDecode(value: String): ByteArray {
    val zeros = value.takeWhile { it == '1' }.length
    // Little-endian base-256 digits of the number the base-58 string spells.
    val bytes = mutableListOf<Int>()
    for (char in value.drop(zeros)) {
        var carry = ALPHABET.indexOf(char)
        require(carry >= 0) { "Not base58btc" }
        for (i in bytes.indices) {
            carry += bytes[i] * BASE
            bytes[i] = carry and BYTE_MASK
            carry = carry shr BYTE_BITS
        }
        while (carry > 0) {
            bytes += carry and BYTE_MASK
            carry = carry shr BYTE_BITS
        }
    }
    return ByteArray(zeros) + bytes.asReversed().map { it.toByte() }.toByteArray()
}
