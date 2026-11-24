package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.base32
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

/**
 * The rkey of the athlete's `feed.trailDetailVote` on [suggestion], derived the same way as [commentHideRkey]: one vote per suggestion,
 * whatever its version, so changing it is a put of the same record even when the AppView no longer reports the old one.
 */
suspend fun trailVoteRkey(suggestion: AtUri): String = uriRkey(suggestion)

/**
 * The rkey of the athlete's `feed.trailDetailVote` confirming [segment]'s [detail], derived like [trailVoteRkey] from both: one
 * confirmation per detail, whatever value it names.
 */
suspend fun trailConfirmationRkey(segment: AtUri, detail: String): String = textRkey("${segment.value}#$detail")

/** The first 20 bytes of SHA-256 over [uri], as lowercase unpadded base32. */
private suspend fun uriRkey(uri: AtUri): String = textRkey(uri.value)

/** The first 20 bytes of SHA-256 over [text], as lowercase unpadded base32. */
private suspend fun textRkey(text: String): String = sha256(text.encodeToByteArray()).copyOf(URI_RKEY_BYTES).base32()

private const val URI_RKEY_BYTES = 20
