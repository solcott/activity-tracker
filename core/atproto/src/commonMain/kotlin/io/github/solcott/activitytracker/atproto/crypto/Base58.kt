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
