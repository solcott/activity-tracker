package io.github.solcott.activitytracker.atproto.crypto

private const val LIMBS = 8
private const val LIMB_BITS = 32
private const val LIMB_MASK = 0xFFFFFFFFL
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val BYTES_PER_LIMB = 4
private const val FIELD_BYTES = 32
private const val FIELD_BITS = 256
private const val EVEN_Y_PREFIX: Byte = 0x02
private const val ODD_Y_PREFIX: Byte = 0x03
private const val UNCOMPRESSED_PREFIX: Byte = 0x04

// The P-256 field prime p, the curve's b, and (p + 1) / 4, the exponent that takes a square root because p ≡ 3 (mod 4).
private val P = limbs("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
private val B = limbs("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b")
private val SQRT_EXPONENT = limbs("3fffffffc0000000400000000000000000000000400000000000000000000000")
private val THREE = LongArray(LIMBS).also { it[0] = 3 }

/**
 * The uncompressed point (`0x04 || X || Y`) for a compressed P-256 point (`0x02`/`0x03 || X`), solving `y² = x³ - 3x + b` for the y with
 * the prefix's parity. Throws [IllegalArgumentException] if X isn't on the curve.
 *
 * Decompressing here means providers only ever see uncompressed points, which every one accepts (WebCrypto's acceptance of compressed ones
 * varies by browser). Public keys only: this arithmetic isn't constant time.
 */
internal fun decompressP256(compressed: ByteArray): ByteArray {
    require(compressed.size == 1 + FIELD_BYTES && (compressed[0] == EVEN_Y_PREFIX || compressed[0] == ODD_Y_PREFIX)) {
        "Not a compressed P-256 point"
    }
    val x = limbs(compressed.copyOfRange(1, compressed.size))
    require(compare(x, P) < 0) { "Not a P-256 point" }
    val rhs = addMod(subMod(mulMod(mulMod(x, x), x), mulMod(THREE, x)), B)
    var y = powMod(rhs, SQRT_EXPONENT)
    require(compare(mulMod(y, y), rhs) == 0) { "Not a P-256 point" }
    val wantOdd = compressed[0] == ODD_Y_PREFIX
    if ((y[0] and 1L == 1L) != wantOdd) y = subMod(LongArray(LIMBS), y)
    return byteArrayOf(UNCOMPRESSED_PREFIX) + compressed.copyOfRange(1, compressed.size) + bytes(y)
}

private fun limbs(hex: String): LongArray = limbs(hex.hexToByteArray())

/** Big-endian bytes to little-endian 32-bit limbs. */
private fun limbs(bytes: ByteArray): LongArray =
    LongArray(LIMBS) { i ->
        var limb = 0L
        for (j in 0 until BYTES_PER_LIMB) {
            limb = (limb shl BYTE_BITS) or (bytes[FIELD_BYTES - (i + 1) * BYTES_PER_LIMB + j].toLong() and BYTE_MASK.toLong())
        }
        limb
    }

private fun bytes(limbs: LongArray): ByteArray =
    ByteArray(FIELD_BYTES) { k ->
        val fromEnd = FIELD_BYTES - 1 - k
        (limbs[fromEnd / BYTES_PER_LIMB] shr (fromEnd % BYTES_PER_LIMB * BYTE_BITS)).toByte()
    }

private fun compare(a: LongArray, b: LongArray): Int {
    for (i in LIMBS - 1 downTo 0) if (a[i] != b[i]) return a[i].compareTo(b[i])
    return 0
}

/** `a - b` in place over the limbs, returning the borrow. */
private fun subtractInto(a: LongArray, b: LongArray): Long {
    var borrow = 0L
    for (i in 0 until LIMBS) {
        val d = a[i] - b[i] - borrow
        a[i] = d and LIMB_MASK
        borrow = if (d < 0) 1 else 0
    }
    return borrow
}

private fun addMod(a: LongArray, b: LongArray): LongArray {
    val sum = LongArray(LIMBS)
    var carry = 0L
    for (i in 0 until LIMBS) {
        val s = a[i] + b[i] + carry
        sum[i] = s and LIMB_MASK
        carry = s ushr LIMB_BITS
    }
    if (carry != 0L || compare(sum, P) >= 0) subtractInto(sum, P)
    return sum
}

private fun subMod(a: LongArray, b: LongArray): LongArray {
    val difference = a.copyOf()
    if (subtractInto(difference, b) != 0L) {
        // Wrapped below zero: adding p back brings it into range (the carry out cancels the borrow).
        var carry = 0L
        for (i in 0 until LIMBS) {
            val s = difference[i] + P[i] + carry
            difference[i] = s and LIMB_MASK
            carry = s ushr LIMB_BITS
        }
    }
    return difference
}

private fun bit(a: LongArray, index: Int): Boolean = (a[index / LIMB_BITS] ushr (index % LIMB_BITS)) and 1L == 1L

/** Double-and-add over [b]'s bits, so it needs only modular addition. */
private fun mulMod(a: LongArray, b: LongArray): LongArray {
    var product = LongArray(LIMBS)
    for (i in FIELD_BITS - 1 downTo 0) {
        product = addMod(product, product)
        if (bit(b, i)) product = addMod(product, a)
    }
    return product
}

private fun powMod(base: LongArray, exponent: LongArray): LongArray {
    var result = LongArray(LIMBS).also { it[0] = 1 }
    for (i in FIELD_BITS - 1 downTo 0) {
        result = mulMod(result, result)
        if (bit(exponent, i)) result = mulMod(result, base)
    }
    return result
}
