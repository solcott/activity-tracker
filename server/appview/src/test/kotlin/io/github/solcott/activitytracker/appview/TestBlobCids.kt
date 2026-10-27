package io.github.solcott.activitytracker.appview

import java.security.MessageDigest

/** The CID a PDS gives [bytes] as a blob: CIDv1, raw codec, sha2-256, in lowercase unpadded base32 with the `b` multibase prefix. */
fun blobCid(bytes: ByteArray): String {
    val binary = byteArrayOf(0x01, 0x55, 0x12, 0x20) + MessageDigest.getInstance("SHA-256").digest(bytes)
    val out = StringBuilder("b")
    var buffer = 0
    var bits = 0
    for (byte in binary) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            bits -= 5
            out.append(BASE32[buffer shr bits and 0x1F])
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    if (bits > 0) out.append(BASE32[buffer shl (5 - bits) and 0x1F])
    return out.toString()
}

/** The name in [named] whose bytes [cid] is the CID of, so a test can name its blobs while records carry their real CIDs. */
fun nameOf(named: Map<String, ByteArray>, cid: String): String? = named.entries.firstOrNull { blobCid(it.value) == cid }?.key

private const val BASE32 = "abcdefghijklmnopqrstuvwxyz234567"
