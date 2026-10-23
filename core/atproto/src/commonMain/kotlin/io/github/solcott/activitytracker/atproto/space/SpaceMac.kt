package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.solcott.activitytracker.atproto.crypto.bytes
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.io.encoding.Base64

/**
 * The MACs atproto Spaces puts on space reads instead of signatures, ported from `@atproto/space`'s `mac.ts` (Oct 22 alpha): a reader sends
 * `Atproto-Space-Want-Mac` with a fresh key share ([SpaceKem]), and the host answers with `Atproto-Space-Mac`, an HMAC-SHA256 under the
 * shared secret over a commit's `data` or a record's URI and CID.
 *
 * Each `for*` call is one request: it agrees a new secret, which never leaves the verifier, so it can't be cached or logged. The MAC covers
 * what the host returns, not a record's value: nothing here checks the value against its CID.
 */
object SpaceMac {
    const val WANT_MAC_HEADER = "Atproto-Space-Want-Mac"
    const val MAC_HEADER = "Atproto-Space-Mac"

    internal const val COMMIT_TYPE = "atproto-space-repo"
    internal const val RECORD_TYPE = "atproto-space-record"
    internal const val VERSION = 1L

    /** A verifier for one `listRepoOps` (or other commit) response from [repo]'s copy of [space], against the author's [authorKem]. */
    suspend fun forCommit(authorKem: String, space: SpaceUri, repo: Did): CommitVerifier =
        CommitVerifier(SpaceKem.encapsulate(authorKem), space, repo)

    /** A verifier for one `getRecord` response for [uri], against the author's [authorKem]. */
    suspend fun forRecord(authorKem: String, uri: AtUri): RecordVerifier = RecordVerifier(SpaceKem.encapsulate(authorKem), uri)

    class CommitVerifier internal constructor(private val kem: SpaceKem, private val space: SpaceUri, private val repo: Did) {
        /** The `Atproto-Space-Want-Mac` value to send. */
        val wantMacHeader: String = formatWantMacHeader(COMMIT_TYPE, kem.keyShare)

        /** Checks the response's `Atproto-Space-Mac` ([macHeader], null if absent) over the commit's [rev] and [data]. */
        suspend fun verify(macHeader: String?, rev: String, data: ByteArray): SpaceMacResult =
            check(kem.sharedSecret, COMMIT_TYPE, macHeader) { context(COMMIT_TYPE, space.value, repo.value, rev) + data }
    }

    class RecordVerifier internal constructor(private val kem: SpaceKem, private val uri: AtUri) {
        /** The `Atproto-Space-Want-Mac` value to send. */
        val wantMacHeader: String = formatWantMacHeader(RECORD_TYPE, kem.keyShare)

        /** Checks the response's `Atproto-Space-Mac` ([macHeader], null if absent) over the record's [cid]. */
        suspend fun verify(macHeader: String?, cid: Cid): SpaceMacResult =
            check(kem.sharedSecret, RECORD_TYPE, macHeader) { context(RECORD_TYPE, uri.value) + cid.bytes() }
    }

    /** `"$type-v1"` then each field as `uint16be(length) || utf8`. */
    internal fun context(type: String, vararg fields: String): ByteArray =
        fields.fold("$type-v$VERSION".encodeToByteArray()) { bytes, field ->
            val utf8 = field.encodeToByteArray()
            bytes + uint16be(utf8.size) + utf8
        }

    @Suppress("ReturnCount") // Each way a response falls short, then the comparison.
    private suspend fun check(secret: ByteArray, type: String, macHeader: String?, message: () -> ByteArray): SpaceMacResult {
        macHeader ?: return SpaceMacResult.Missing
        val mac =
            parseMacHeader(macHeader, type).getOrElse {
                return SpaceMacResult.Malformed(it.message.orEmpty())
            }
        val bytes =
            runCatching(message).getOrElse {
                return SpaceMacResult.Malformed(it.message.orEmpty())
            }
        return if (constantTimeEquals(hmacSha256(secret, bytes), mac)) SpaceMacResult.Valid else SpaceMacResult.Mismatch
    }

    internal suspend fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
        CryptographyProvider.Default.get(HMAC)
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignature(message)

    /** Equal lengths compare every byte, wherever they first differ. */
    internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // ---- Headers (RFC 8941 dictionaries, written and read only as far as these two need) ----

    private const val MAC_LENGTH = 32

    /** RFC 8941 byte sequences are padded base64; unpadded is accepted too. */
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    internal fun formatWantMacHeader(type: String, keyShare: String): String =
        """type=${sfString(type)}, ver=$VERSION, key_share=${sfString(keyShare)}"""

    /** The 32-byte MAC of an `Atproto-Space-Mac` value whose `type` is [type] and `ver` is 1, or a failure saying why not. */
    internal fun parseMacHeader(value: String, type: String): Result<ByteArray> = runCatching {
        val fields = StructuredDictionary.parse(value)
        require(fields["type"] == SfItem.Str(type)) { "Unsupported MAC type, expected $type" }
        require(fields["ver"] == SfItem.Integer(VERSION)) { "Unsupported MAC version for $type" }
        val mac = requireNotNull(fields["mac"] as? SfItem.Bytes) { "Missing or malformed mac" }.base64
        val bytes = base64.decode(mac)
        require(bytes.size == MAC_LENGTH) { "MAC isn't $MAC_LENGTH bytes" }
        bytes
    }

    private fun sfString(value: String): String {
        require(value.all { it in ' '..'~' }) { "Not an RFC 8941 string" }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

/** What [SpaceMac]'s verifiers make of a response. Only [Valid] means the host vouched for it under this request's secret. */
sealed interface SpaceMacResult {
    data object Valid : SpaceMacResult

    /** The response had no `Atproto-Space-Mac`. */
    data object Missing : SpaceMacResult

    /** A well-formed MAC that doesn't match: a stale KEM key (refresh the DID document) or a tampered response. */
    data object Mismatch : SpaceMacResult

    /** The header was unreadable, or had the wrong `type` or `ver`. */
    data class Malformed(val reason: String) : SpaceMacResult
}
