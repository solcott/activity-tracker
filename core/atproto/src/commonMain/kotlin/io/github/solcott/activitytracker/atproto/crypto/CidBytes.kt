package io.github.solcott.activitytracker.atproto.crypto

import io.github.solcott.activitytracker.lexicon.Cid

private const val CID_V1: Byte = 0x01

/**
 * The binary form of a CIDv1 in multibase `b` (lowercase base32, unpadded), as atproto writes CIDs. A Spaces record MAC covers these bytes.
 * Throws [IllegalArgumentException] for a CIDv0, another multibase, or bad base32.
 */
fun Cid.bytes(): ByteArray {
    require(value.startsWith("b")) { "Not a base32 CIDv1" }
    val out = runCatching { base32Decode(value.substring(1)) }.getOrElse { throw IllegalArgumentException("Not a base32 CIDv1", it) }
    require(out.firstOrNull() == CID_V1) { "Not a CIDv1" }
    return out
}
