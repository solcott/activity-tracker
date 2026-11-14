package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.http.Headers

/**
 * The MACs atproto Spaces puts on space reads instead of signatures, ported from `@atproto/space`'s `mac.ts` (Nov 12 alpha): RFC 9421 HTTP
 * message signatures using `hmac-sha256`, keyed by a per-request shared secret ([SpaceKem]). A reader asks for one with `Accept-Signature`,
 * naming its key share as the `keyid` and a profile as the `tag`; the host answers with a header describing the commit
 * (`Atproto-Space-Commit`) or record (`Atproto-Space-Record`) it returned, and a signature covering that header as a structured field.
 *
 * Each `for*` call is one request: it agrees a new secret, which never leaves the verifier, so it can't be cached or logged. The MAC covers
 * what the host says it returned, not a record's value: nothing here checks the value against its CID.
 */
object SpaceMac {
    const val ACCEPT_SIGNATURE = "Accept-Signature"
    const val COMMIT_HEADER = "Atproto-Space-Commit"
    const val RECORD_HEADER = "Atproto-Space-Record"

    internal const val COMMIT_PROFILE = "atproto-space-commit"
    internal const val RECORD_PROFILE = "atproto-space-record"
    internal const val ALG = "hmac-sha256"
    internal const val COMMIT_VERSION = 1L

    /** The label this reader asks for its MAC under; the host answers under it, though the MAC is found by its tag. */
    private const val LABEL = "mac"

    /** A verifier for one `listRepoOps` (or other commit) response from [repo]'s copy of [space], against the author's [authorKem]. */
    suspend fun forCommit(authorKem: String, space: SpaceUri, repo: Did): CommitVerifier =
        CommitVerifier(SpaceKem.encapsulate(authorKem), space, repo)

    /** A verifier for one `getRecord` response for [uri], against the author's [authorKem]. */
    suspend fun forRecord(authorKem: String, uri: AtUri): RecordVerifier = RecordVerifier(SpaceKem.encapsulate(authorKem), uri)

    class CommitVerifier internal constructor(private val kem: SpaceKem, private val space: SpaceUri, private val repo: Did) {
        /** The `Accept-Signature` value to send. */
        val acceptSignature: String = formatRequest(COMMIT_PROFILE, kem.keyShare)

        /**
         * Checks the response's MAC, and that the `Atproto-Space-Commit` it covers describes [commit] in [space] and [repo] as asked: the
         * body's commit must say the same, and name them too.
         */
        suspend fun verify(headers: Headers, commit: Space.ListRepoOps.Commit): SpaceMacResult =
            check(kem, COMMIT_PROFILE, headers) { fields ->
                fields.bare("ver") == SfBare.Integer(commit.ver.toLong()) &&
                    fields.bare("repo") == SfBare.Str(repo.value) &&
                    fields.bare("space") == SfBare.Str(space.value) &&
                    fields.bare("rev") == SfBare.Str(commit.rev) &&
                    fields.bare("data") == SfBare.Bytes(commit.data.decode()) &&
                    commit.repo == repo &&
                    commit.space == space
            }
    }

    class RecordVerifier internal constructor(private val kem: SpaceKem, private val uri: AtUri) {
        /** The `Accept-Signature` value to send. */
        val acceptSignature: String = formatRequest(RECORD_PROFILE, kem.keyShare)

        /** Checks the response's MAC, and that the `Atproto-Space-Record` it covers names the [uri] asked for and the body's [cid]. */
        suspend fun verify(headers: Headers, cid: Cid): SpaceMacResult =
            check(kem, RECORD_PROFILE, headers) { fields ->
                fields.bare("uri") == SfBare.Str(uri.value) && fields.bare("cid") == SfBare.Str(cid.value)
            }
    }

    /** What a MAC covers for [profile]: its header, flagged `sf` as a structured field. */
    internal fun components(profile: String): List<SfItem> =
        listOf(HttpMessageSignature.component(profile, mapOf("sf" to SfBare.Bool(true))))

    /** The header covered by [profile]'s MAC, named as on the wire. */
    internal fun coveredHeader(profile: String): String = if (profile == COMMIT_PROFILE) COMMIT_HEADER else RECORD_HEADER

    /** `mac=("atproto-space-commit";sf);keyid="did:key:zDna...";tag="atproto-space-commit"`, or the record profile. */
    internal fun formatRequest(profile: String, keyShare: String): String {
        val params = SfInnerList(components(profile), linkedMapOf("keyid" to SfBare.Str(keyShare), "tag" to SfBare.Str(profile)))
        return "$LABEL=${StructuredFields.serializeInnerList(params)}"
    }

    @Suppress("ReturnCount") // Each way a response falls short, then the comparisons.
    private suspend fun check(
        kem: SpaceKem,
        profile: String,
        headers: Headers,
        matches: (Map<String, SfMember>) -> Boolean,
    ): SpaceMacResult {
        val input = headers[HttpMessageSignature.SIGNATURE_INPUT]
        val signature = headers[HttpMessageSignature.SIGNATURE]
        val covered = headers[coveredHeader(profile)]
        if (input == null && signature == null && covered == null) return SpaceMacResult.Missing
        val found = runCatching {
            HttpMessageSignature.find(input, signature, HttpMessageSignature.Requirements(profile, components(profile), ALG, kem.keyShare))
        }
            .getOrElse {
                return SpaceMacResult.Malformed(it.message.orEmpty())
            }
        val fields = runCatching {
            StructuredFields.parseDictionary(requireNotNull(covered) { "Missing ${coveredHeader(profile)}" })
        }
            .getOrElse {
                return SpaceMacResult.Malformed(it.message.orEmpty())
            }
        // The `sf` flag covers the header as re-serialized from its parsed form.
        val base = runCatching {
            HttpMessageSignature.signatureBase(
                components(profile),
                listOf(StructuredFields.serializeDictionary(fields)),
                found.signatureParams,
            )
        }
            .getOrElse {
                return SpaceMacResult.Malformed(it.message.orEmpty())
            }
        if (!constantTimeEquals(hmacSha256(kem.sharedSecret, base), found.signature)) return SpaceMacResult.Mismatch
        return if (matches(fields)) SpaceMacResult.Valid else SpaceMacResult.Mismatch
    }

    /** A dictionary member's bare value, ignoring its parameters; null for an inner list or a missing key. */
    private fun Map<String, SfMember>.bare(key: String): SfBare? = (this[key] as? SfItem)?.bare

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
}

/** What [SpaceMac]'s verifiers make of a response. Only [Valid] means the host vouched for it under this request's secret. */
sealed interface SpaceMacResult {
    data object Valid : SpaceMacResult

    /** The response carried no MAC at all: no `Signature-Input`, `Signature` or covered header. */
    data object Missing : SpaceMacResult

    /**
     * A well-formed MAC that doesn't match, or a covered header that doesn't describe the body or the request: a stale KEM key (refresh the
     * DID document) or a tampered response.
     */
    data object Mismatch : SpaceMacResult

    /** The signature headers were unreadable, or didn't meet the profile's requirements. */
    data class Malformed(val reason: String) : SpaceMacResult
}
