package io.github.solcott.activitytracker.atproto.space

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.solcott.activitytracker.atproto.crypto.bytes
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.atproto.Space
import kotlin.io.encoding.Base64

/**
 * The repo host's side of [SpaceMac], as `@atproto/space`'s `decapsulate` and `formatMacHeader` do it: answering a reader's
 * `Atproto-Space-Want-Mac` with an `Atproto-Space-Mac`. The app never hosts space repos; this is for test fakes of a Spaces PDS, so the
 * protocol lives in one place.
 */
object SpaceMacHost {
    /** An author's `#atproto_kem` key pair; [multikey] is what its DID document publishes. */
    class KemKey internal constructor(internal val private: ECDH.PrivateKey, internal val publicRaw: ByteArray) {
        val multikey: String = p256DidKey(publicRaw).removePrefix("did:key:")
    }

    suspend fun generateKemKey(): KemKey {
        val pair = CryptographyProvider.Default.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
        return KemKey(pair.privateKey, pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW))
    }

    /** The `Atproto-Space-Mac` for [commit], given the reader's [wantMac], or null when it isn't a usable repo MAC request. */
    suspend fun commitMacHeader(kem: KemKey, wantMac: String?, commit: Space.ListRepoOps.Commit): String? =
        macHeader(kem, wantMac, SpaceMac.COMMIT_TYPE) {
            SpaceMac.context(SpaceMac.COMMIT_TYPE, commit.space.value, commit.repo.value, commit.rev) + commit.data.decode()
        }

    /** The `Atproto-Space-Mac` for a record, given the reader's [wantMac], or null when it isn't a usable record MAC request. */
    suspend fun recordMacHeader(kem: KemKey, wantMac: String?, uri: AtUri, cid: Cid): String? =
        macHeader(kem, wantMac, SpaceMac.RECORD_TYPE) { SpaceMac.context(SpaceMac.RECORD_TYPE, uri.value) + cid.bytes() }

    private suspend fun macHeader(kem: KemKey, wantMac: String?, type: String, message: () -> ByteArray): String? {
        val keyShare = wantMac?.let { keyShareOf(it, type) } ?: return null
        val secret = SpaceKem.decapsulate(kem.private, kem.publicRaw, keyShare)
        val mac = Base64.Default.withPadding(Base64.PaddingOption.PRESENT).encode(SpaceMac.hmacSha256(secret, message()))
        return """type="$type", ver=${SpaceMac.VERSION}, mac=:$mac:"""
    }

    /** The `key_share` of an `Atproto-Space-Want-Mac` asking for a [type] MAC at version 1, or null. */
    private fun keyShareOf(wantMac: String, type: String): String? {
        val fields = runCatching { StructuredDictionary.parse(wantMac) }.getOrNull()
        val wanted = fields?.get("type") == SfItem.Str(type) && fields["ver"] == SfItem.Integer(SpaceMac.VERSION)
        return (fields?.get("key_share") as? SfItem.Str)?.value?.takeIf { wanted }
    }
}
