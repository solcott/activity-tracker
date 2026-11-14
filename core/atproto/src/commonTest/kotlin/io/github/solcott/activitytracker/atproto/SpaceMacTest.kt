package io.github.solcott.activitytracker.atproto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.solcott.activitytracker.atproto.SpaceMacVectors as V
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.atproto.crypto.p256PublicKey
import io.github.solcott.activitytracker.atproto.space.HttpMessageSignature
import io.github.solcott.activitytracker.atproto.space.SpaceKem
import io.github.solcott.activitytracker.atproto.space.SpaceMac
import io.github.solcott.activitytracker.atproto.space.SpaceMacHost
import io.github.solcott.activitytracker.atproto.space.SpaceMacResult
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.LexBytes
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.http.Headers
import io.ktor.http.headersOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** HPKE secret export, MAC requests and the MACs answering them, against [SpaceMacVectors]. */
class SpaceMacTest {
    private suspend fun privateKey(hex: String) =
        CryptographyProvider.Default.get(ECDH)
            .privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.RAW, hex.hexToByteArray())

    private suspend fun fixedKem(): SpaceKem =
        SpaceKem.encapsulate(V.AUTHOR_MULTIKEY, privateKey(V.EPHEMERAL_PRIVATE), V.EPHEMERAL_RAW.hexToByteArray())

    private suspend fun commitVerifier(space: String = V.SPACE, repo: String = V.REPO) =
        SpaceMac.CommitVerifier(fixedKem(), SpaceUri(space), Did(repo))

    private suspend fun recordVerifier(uri: String = V.URI) = SpaceMac.RecordVerifier(fixedKem(), AtUri(uri))

    private fun commit(rev: String = V.REV, data: String = V.DATA, space: String = V.SPACE, repo: String = V.REPO) =
        Space.ListRepoOps.Commit(1, Did(repo), SpaceUri(space), rev, LexBytes.of(data.hexToByteArray()))

    private fun commitHeaders(
        covered: String? = V.COMMIT_HEADER,
        input: String? = V.COMMIT_SIGNATURE_INPUT,
        signature: String? = V.COMMIT_SIGNATURE,
    ): Headers = headers(SpaceMac.COMMIT_HEADER to covered, "Signature-Input" to input, "Signature" to signature)

    private fun recordHeaders(
        covered: String? = V.RECORD_HEADER,
        input: String? = V.RECORD_SIGNATURE_INPUT,
        signature: String? = V.RECORD_SIGNATURE,
    ) = headers(SpaceMac.RECORD_HEADER to covered, "Signature-Input" to input, "Signature" to signature)

    private fun headers(vararg values: Pair<String, String?>): Headers =
        headersOf(*values.mapNotNull { (k, v) -> v?.let { k to listOf(it) } }.toTypedArray())

    /** [value] with its last character's lowest bit flipped. */
    private fun flipped(value: String, from: Int = value.length - 1) =
        value.substring(0, from) + (value[from].code xor 1).toChar() + value.substring(from + 1)

    @Test
    fun encapsulateMatchesReference() = runTest {
        val kem = fixedKem()
        assertEquals(V.KEY_SHARE, kem.keyShare)
        assertEquals(V.SHARED_SECRET, kem.sharedSecret.toHexString())
        assertEquals(
            V.SHARED_SECRET,
            SpaceKem.decapsulate(privateKey(V.AUTHOR_PRIVATE), V.AUTHOR_RAW.hexToByteArray(), V.KEY_SHARE).toHexString(),
        )
    }

    @Test
    fun secretIsRfc9180ExportOnlyHpke() = runTest {
        val enc = p256DidKey(V.HPKE_ENC.hexToByteArray())
        val secret = SpaceKem.decapsulate(privateKey(V.HPKE_SK_RM), p256PublicKey(V.HPKE_PK_RM_DID_KEY), enc)
        assertEquals(V.HPKE_SECRET, secret.toHexString())
    }

    @Test
    fun generatedKeysDiffer() = runTest {
        val first = SpaceKem.encapsulate(V.AUTHOR_MULTIKEY)
        val second = SpaceKem.encapsulate(V.AUTHOR_MULTIKEY)
        assertTrue(first.keyShare.startsWith("did:key:zDna"), first.keyShare)
        assertNotEquals(first.keyShare, second.keyShare)
        assertNotEquals(first.sharedSecret.toHexString(), second.sharedSecret.toHexString())
    }

    @Test
    fun requestsMatchReference() = runTest {
        assertEquals(V.COMMIT_REQUEST, commitVerifier().acceptSignature)
        assertEquals(V.RECORD_REQUEST, recordVerifier().acceptSignature)
    }

    @Test
    fun referenceMacsAreValid() = runTest {
        assertEquals(SpaceMacResult.Valid, commitVerifier().verify(commitHeaders(), commit()))
        assertEquals(SpaceMacResult.Valid, recordVerifier().verify(recordHeaders(), Cid(V.CID)))
    }

    @Test
    fun hostAnswersReferenceRequestsAsTheReferenceDoes() = runTest {
        val kem = SpaceMacHost.kemKeyOf(privateKey(V.AUTHOR_PRIVATE), V.AUTHOR_RAW.hexToByteArray())
        assertEquals(
            mapOf(
                SpaceMac.COMMIT_HEADER to V.COMMIT_HEADER,
                "Signature-Input" to V.COMMIT_SIGNATURE_INPUT,
                "Signature" to V.COMMIT_SIGNATURE,
            ),
            SpaceMacHost.commitMacHeaders(kem, V.COMMIT_REQUEST, commit()),
        )
        assertEquals(
            mapOf(
                SpaceMac.RECORD_HEADER to V.RECORD_HEADER,
                "Signature-Input" to V.RECORD_SIGNATURE_INPUT,
                "Signature" to V.RECORD_SIGNATURE,
            ),
            SpaceMacHost.recordMacHeaders(kem, V.RECORD_REQUEST, AtUri(V.URI), Cid(V.CID)),
        )
    }

    @Test
    fun macsAreFoundByTagAndCanonicalized() = runTest {
        // Another label, extra parameters such as `created`, other signatures and the covered header written without spaces are all fine:
        // the tag finds the MAC, and the `sf` flag covers the header as re-serialized.
        val mac = V.COMMIT_SIGNATURE.removePrefix("mac=")
        val withCreated = V.COMMIT_SIGNATURE_INPUT.removePrefix("mac=") + ";created=1700000000"
        val kem = SpaceMacHost.kemKeyOf(privateKey(V.AUTHOR_PRIVATE), V.AUTHOR_RAW.hexToByteArray())
        val answered = assertNotNull(SpaceMacHost.commitMacHeaders(kem, "label2=" + V.COMMIT_REQUEST.removePrefix("mac="), commit()))
        assertEquals("label2=$mac", answered["Signature"])
        assertEquals(SpaceMacResult.Valid, commitVerifier().verify(Headers.build { answered.forEach { (k, v) -> append(k, v) } }, commit()))
        // A host that adds `created` signs it too, and a reader accepts it without reading it.
        val createdBase =
            HttpMessageSignature.signatureBase(SpaceMac.components(SpaceMac.COMMIT_PROFILE), listOf(V.COMMIT_HEADER), withCreated)
        val createdMac = Base64.encode(SpaceMac.hmacSha256(V.SHARED_SECRET.hexToByteArray(), createdBase))
        assertEquals(
            SpaceMacResult.Valid,
            commitVerifier().verify(commitHeaders(input = "mac=$withCreated", signature = "mac=:$createdMac:"), commit()),
        )
        for (headers in
            listOf(
                commitHeaders(covered = V.COMMIT_HEADER.replace(", ", ",")),
                commitHeaders(covered = "  ${V.COMMIT_HEADER}  "),
                commitHeaders(
                    input = """sig=("authorization");tag="atproto-space-credential", ${V.COMMIT_SIGNATURE_INPUT}""",
                    signature = "sig=:AAAA:, ${V.COMMIT_SIGNATURE}",
                ),
            )) {
            assertEquals(SpaceMacResult.Valid, commitVerifier().verify(headers, commit()), headers.toString())
        }
        // `created` is covered: added after signing, the MAC no longer matches.
        assertEquals(
            SpaceMacResult.Mismatch,
            commitVerifier().verify(commitHeaders(input = "mac=$withCreated", signature = "mac=$mac"), commit()),
        )
    }

    @Test
    fun badSignatureHeadersAreMalformed() = runTest {
        val params = V.COMMIT_SIGNATURE_INPUT.removePrefix("mac=")
        for (headers in
            listOf(
                commitHeaders(input = null),
                commitHeaders(signature = null),
                commitHeaders(covered = null),
                commitHeaders(input = "mac=$params, again=$params", signature = "${V.COMMIT_SIGNATURE}, again=:AAAA:"),
                commitHeaders(input = params.replace("atproto-space-commit\";sf", "atproto-space-commit\"").let { "mac=$it" }),
                commitHeaders(input = "mac=" + params.replace(";sf)", ";sf \"authorization\")")),
                commitHeaders(input = "mac=$params;alg=\"ecdsa-p256-sha256\""),
                commitHeaders(input = "mac=" + params.replace(V.KEY_SHARE, V.AUTHOR_DID_KEY)),
                commitHeaders(input = "mac=" + params.replace("tag=\"atproto-space-commit\"", "tag=\"atproto-space-repo\"")),
                commitHeaders(signature = "other=:AAAA:"),
                commitHeaders(signature = "mac=\"not bytes\""),
                commitHeaders(signature = "${V.COMMIT_SIGNATURE};p=1"),
                commitHeaders(signature = "mac=:not base64!:"),
                commitHeaders(covered = "ver=1, repo=\"unterminated"),
                commitHeaders(input = "Mac=$params"),
            )) {
            assertIs<SpaceMacResult.Malformed>(commitVerifier().verify(headers, commit()), headers.toString())
        }
        assertIs<SpaceMacResult.Malformed>(recordVerifier().verify(commitHeaders(), Cid(V.CID)))
    }

    @Test
    fun anyChangedInputIsMismatch() = runTest {
        val verifier = commitVerifier()
        val headers = commitHeaders()
        assertEquals(SpaceMacResult.Mismatch, verifier.verify(headers, commit(data = flipped(V.DATA, 0))))
        assertEquals(SpaceMacResult.Mismatch, verifier.verify(headers, commit(rev = flipped(V.REV))))
        assertEquals(SpaceMacResult.Mismatch, commitVerifier(space = flipped(V.SPACE)).verify(headers, commit()))
        assertEquals(SpaceMacResult.Mismatch, commitVerifier(repo = flipped(V.REPO)).verify(headers, commit()))
        // A body naming another space or repo than asked, even with the header saying the same.
        assertEquals(SpaceMacResult.Mismatch, verifier.verify(headers, commit(space = flipped(V.SPACE))))
        assertEquals(SpaceMacResult.Mismatch, verifier.verify(commitHeaders(covered = V.COMMIT_HEADER.replace("ver=1", "ver=2")), commit()))
        assertEquals(
            SpaceMacResult.Mismatch,
            verifier.verify(commitHeaders(signature = V.COMMIT_SIGNATURE.replace("Okjw", "Okjx")), commit()),
        )
        // Reordered fields serialize differently, so the MAC no longer covers them.
        val reordered = V.COMMIT_HEADER.split(", ").reversed().joinToString(", ")
        assertEquals(SpaceMacResult.Mismatch, verifier.verify(commitHeaders(covered = reordered), commit()))

        assertEquals(SpaceMacResult.Mismatch, recordVerifier(flipped(V.URI)).verify(recordHeaders(), Cid(V.CID)))
        // A CID with one bit of its digest flipped (the last base32 character holds padding bits, so the one before it).
        assertEquals(SpaceMacResult.Mismatch, recordVerifier().verify(recordHeaders(), Cid(flipped(V.CID, V.CID.length - 2))))
    }

    @Test
    fun noMacIsMissing() = runTest {
        assertEquals(SpaceMacResult.Missing, commitVerifier().verify(Headers.Empty, commit()))
        assertEquals(SpaceMacResult.Missing, recordVerifier().verify(Headers.Empty, Cid(V.CID)))
    }

    @Test
    fun forRecordAgreesAFreshKey() = runTest {
        val first = SpaceMac.forRecord(V.AUTHOR_MULTIKEY, AtUri(V.URI))
        val second = SpaceMac.forRecord(V.AUTHOR_MULTIKEY, AtUri(V.URI))
        assertNotEquals(first.acceptSignature, second.acceptSignature)
        // A MAC for someone else's key share never validates: its keyid isn't this request's.
        assertIs<SpaceMacResult.Malformed>(first.verify(recordHeaders(), Cid(V.CID)))
    }

    @Test
    fun hostRefusesUnusableRequests() = runTest {
        val kem = SpaceMacHost.generateKemKey()
        val request = SpaceMac.forCommit(kem.multikey, SpaceUri(V.SPACE), Did(V.REPO)).acceptSignature
        val params = request.removePrefix("mac=")
        assertNull(SpaceMacHost.commitMacHeaders(kem, null, commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, "garbage(", commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, V.RECORD_REQUEST, commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, "$request, again=$params", commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, "$request;created=1", commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, "$request;alg=\"hmac-sha512\"", commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, request.replace(Regex("keyid=\"[^\"]+\""), "keyid=\"${V.K256_DID_KEY}\""), commit()))
        assertNull(SpaceMacHost.commitMacHeaders(kem, request.replace(";sf", ""), commit()))
        // Requests with other tags are ignored, and `alg="hmac-sha256"` is allowed.
        assertTrue(SpaceMacHost.commitMacHeaders(kem, """other=("x");tag="x", $request;alg="hmac-sha256"""", commit()) != null)
    }
}
