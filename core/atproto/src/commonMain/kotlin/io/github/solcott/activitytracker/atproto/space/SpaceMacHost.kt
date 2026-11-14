package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.atproto.Space
import kotlin.io.encoding.Base64

/**
 * The repo host's side of [SpaceMac], as `@atproto/space`'s `decapsulate`, `parseMacRequest` and `create*MacHeaders` do it: answering a
 * reader's `Accept-Signature` with the covered header, `Signature-Input` and `Signature`. The app never hosts space repos; this is for test
 * fakes of a Spaces PDS, so the protocol lives in one place.
 */
object SpaceMacHost {
    /** An author's `#atproto_kem` key pair; [multikey] is what its DID document publishes. */
    class KemKey internal constructor(internal val private: ECDH.PrivateKey, internal val publicRaw: ByteArray) {
        val multikey: String = p256DidKey(publicRaw).removePrefix("did:key:")
    }

    /** A KEM key pair from a known private key, for vectors. */
    internal fun kemKeyOf(private: ECDH.PrivateKey, publicRaw: ByteArray) = KemKey(private, publicRaw)

    suspend fun generateKemKey(): KemKey {
        val pair = CryptographyProvider.Default.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
        return KemKey(pair.privateKey, pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW))
    }

    /** The response headers for [commit], or null when [acceptSignature] isn't a usable commit MAC request (`InvalidSpaceMac`). */
    suspend fun commitMacHeaders(kem: KemKey, acceptSignature: String?, commit: Space.ListRepoOps.Commit): Map<String, String>? =
        macHeaders(
            kem,
            acceptSignature,
            SpaceMac.COMMIT_PROFILE,
            linkedMapOf(
                "ver" to SfItem(SfBare.Integer(commit.ver.toLong())),
                "repo" to SfItem(SfBare.Str(commit.repo.value)),
                "space" to SfItem(SfBare.Str(commit.space.value)),
                "rev" to SfItem(SfBare.Str(commit.rev)),
                "data" to SfItem(SfBare.Bytes(commit.data.decode())),
            ),
        )

    /** The response headers for a record, or null when [acceptSignature] isn't a usable record MAC request. */
    suspend fun recordMacHeaders(kem: KemKey, acceptSignature: String?, uri: AtUri, cid: Cid): Map<String, String>? =
        macHeaders(
            kem,
            acceptSignature,
            SpaceMac.RECORD_PROFILE,
            linkedMapOf("uri" to SfItem(SfBare.Str(uri.value)), "cid" to SfItem(SfBare.Str(cid.value))),
        )

    private suspend fun macHeaders(
        kem: KemKey,
        acceptSignature: String?,
        profile: String,
        fields: Map<String, SfMember>,
    ): Map<String, String>? {
        val (label, params) = acceptSignature?.let { request(it, profile) } ?: return null
        val secret =
            (params.params["keyid"] as? SfBare.Str)?.let {
                runCatching { SpaceKem.decapsulate(kem.private, kem.publicRaw, it.value) }.getOrNull()
            }
        return secret?.let {
            val covered = StructuredFields.serializeDictionary(fields)
            val signatureParams = StructuredFields.serializeInnerList(params)
            val base = HttpMessageSignature.signatureBase(SpaceMac.components(profile), listOf(covered), signatureParams)
            val mac = Base64.Default.withPadding(Base64.PaddingOption.PRESENT).encode(SpaceMac.hmacSha256(it, base))
            mapOf(
                SpaceMac.coveredHeader(profile) to covered,
                HttpMessageSignature.SIGNATURE_INPUT to "$label=$signatureParams",
                HttpMessageSignature.SIGNATURE to "$label=:$mac:",
            )
        }
    }

    /**
     * The one request for [profile] in an `Accept-Signature`, by label, or null when there's none, more than one, or it asks for anything
     * but the profile's component and `keyid`, `tag` and `alg="hmac-sha256"`.
     */
    private fun request(acceptSignature: String, profile: String): HttpMessageSignature.Tagged? {
        val requests = runCatching { StructuredFields.parseDictionary(acceptSignature) }.getOrNull()
        val tagged = requests?.let { HttpMessageSignature.withTag(it, profile).singleOrNull() }
        return tagged?.takeIf { (_, params) ->
            HttpMessageSignature.components(params.items) == HttpMessageSignature.components(SpaceMac.components(profile)) &&
                params.params.keys.all { it in REQUEST_PARAMS } &&
                (params.params["alg"] ?: SfBare.Str(SpaceMac.ALG)) == SfBare.Str(SpaceMac.ALG)
        }
    }

    private val REQUEST_PARAMS = setOf("keyid", "tag", "alg")
}
