package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.atproto.crypto.base32
import java.security.MessageDigest

/** The CID a PDS gives [bytes] as a blob: CIDv1, raw codec, sha2-256, in lowercase unpadded base32 with the `b` multibase prefix. */
fun blobCid(bytes: ByteArray): String =
    "b" + (byteArrayOf(0x01, 0x55, 0x12, 0x20) + MessageDigest.getInstance("SHA-256").digest(bytes)).base32()

/** The name in [named] whose bytes [cid] is the CID of, so a test can name its blobs while records carry their real CIDs. */
fun nameOf(named: Map<String, ByteArray>, cid: String): String? = named.entries.firstOrNull { blobCid(it.value) == cid }?.key
