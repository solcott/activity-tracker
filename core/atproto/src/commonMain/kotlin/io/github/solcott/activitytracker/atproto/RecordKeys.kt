package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.sha256
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * The rkey of the athlete's `feed.commentHide` of [comment]: the first 20 bytes of SHA-256 over its AT-URI, as lowercase unpadded base32
 * (32 characters, all valid in a record key). One per comment, so hiding is an idempotent put and unhiding a delete with no lookup; the
 * AppView ignores a hide stored under any other rkey. Suspends because WebCrypto, the web's only SHA-256, has no blocking hash.
 */
suspend fun commentHideRkey(comment: AtUri): String = uriRkey(comment)

/**
 * The rkey of the athlete's `feed.segmentStar` of [segment], derived the same way as [commentHideRkey]: one star per segment, so starring
 * is an idempotent put on any device and unstarring a delete with no lookup.
 */
suspend fun segmentStarRkey(segment: AtUri): String = uriRkey(segment)

/** The first 20 bytes of SHA-256 over [uri], as lowercase unpadded base32. */
private suspend fun uriRkey(uri: AtUri): String = sha256(uri.value.encodeToByteArray()).copyOf(URI_RKEY_BYTES).base32()

private const val URI_RKEY_BYTES = 20
private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
private const val BITS_PER_CHAR = 5
private const val BYTE_MASK = 0xff
private const val CHAR_MASK = 0x1f

/** RFC 4648 base32, lowercase and without padding. */
private fun ByteArray.base32(): String = buildString {
    var buffer = 0
    var bits = 0
    for (byte in this@base32) {
        buffer = (buffer shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
        bits += Byte.SIZE_BITS
        while (bits >= BITS_PER_CHAR) {
            bits -= BITS_PER_CHAR
            append(BASE32_ALPHABET[(buffer shr bits) and CHAR_MASK])
        }
    }
    if (bits > 0) append(BASE32_ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
}
