package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.atproto.crypto.bytes
import io.github.solcott.activitytracker.lexicon.Cid
import java.security.MessageDigest

/**
 * Whether [bytes] are the blob [cid] names: a CIDv1 with a SHA-256 multihash of exactly these bytes. A PDS is untrusted, so a blob fetched
 * from one is checked before anything keyed by its CID trusts it; any other CID (another hash, a CIDv0, bad base32) never matches.
 */
internal fun blobMatchesCid(cid: Cid, bytes: ByteArray): Boolean {
    // Version 1, then a one-byte codec (raw, dag-cbor…), then the multihash: sha2-256, 32 bytes, the digest.
    val multihash = runCatching { cid.bytes() }.getOrNull()?.let { it.copyOfRange(minOf(2, it.size), it.size) }
    val sha256 =
        multihash != null && multihash.size == SHA256_MULTIHASH_SIZE && multihash[0] == SHA256_CODE && multihash[1] == SHA256_LENGTH
    return sha256 && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), multihash.copyOfRange(2, multihash.size))
}

private const val SHA256_CODE: Byte = 0x12
private const val SHA256_LENGTH: Byte = 0x20
private const val SHA256_MULTIHASH_SIZE = 34
