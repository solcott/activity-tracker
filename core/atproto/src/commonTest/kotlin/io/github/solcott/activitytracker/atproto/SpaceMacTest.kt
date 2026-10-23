package io.github.solcott.activitytracker.atproto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.solcott.activitytracker.atproto.SpaceMacVectors as V
import io.github.solcott.activitytracker.atproto.space.SpaceKem
import io.github.solcott.activitytracker.atproto.space.SpaceMac
import io.github.solcott.activitytracker.atproto.space.SpaceMacResult
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Key agreement, MACs and their headers, against [SpaceMacVectors]. */
class SpaceMacTest {
    private suspend fun fixedKem(): SpaceKem {
        val ephemeral =
            CryptographyProvider.Default.get(ECDH)
                .privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.RAW, V.EPHEMERAL_PRIVATE.hexToByteArray())
        return SpaceKem.encapsulate(V.AUTHOR_MULTIKEY, ephemeral, V.EPHEMERAL_RAW.hexToByteArray())
    }

    private suspend fun commitVerifier(space: String = V.SPACE, repo: String = V.REPO) =
        SpaceMac.CommitVerifier(fixedKem(), SpaceUri(space), Did(repo))

    private suspend fun recordVerifier(uri: String = V.URI) = SpaceMac.RecordVerifier(fixedKem(), AtUri(uri))

    /** [hex] with the lowest bit of its first byte flipped. */
    private fun flippedBytes(hex: String): ByteArray = hex.hexToByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }

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
            SpaceKem.encapsulate(V.AUTHOR_DID_KEY, ecdhKey(), V.EPHEMERAL_RAW.hexToByteArray()).sharedSecret.toHexString(),
        )
    }

    private suspend fun ecdhKey() =
        CryptographyProvider.Default.get(ECDH)
            .privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(
                EC.PrivateKey.Format.DER.SEC1,
                ("30310201010420" + V.EPHEMERAL_PRIVATE + "a00a06082a8648ce3d030107").hexToByteArray(),
            )

    @Test
    fun generatedKeysDiffer() = runTest {
        val first = SpaceKem.encapsulate(V.AUTHOR_MULTIKEY)
        val second = SpaceKem.encapsulate(V.AUTHOR_MULTIKEY)
        assertTrue(first.keyShare.startsWith("did:key:zDna"), first.keyShare)
        assertNotEquals(first.keyShare, second.keyShare)
        assertNotEquals(first.sharedSecret.toHexString(), second.sharedSecret.toHexString())
    }

    @Test
    fun wantMacHeadersMatchReference() = runTest {
        assertEquals(V.WANT_MAC_COMMIT, commitVerifier().wantMacHeader)
        assertEquals(V.WANT_MAC_RECORD, recordVerifier().wantMacHeader)
    }

    @Test
    fun macHeaderParsesLeniently() {
        val mac = V.COMMIT_MAC.hexToByteArray()
        val base64 = "5MEKSII27AWjDR9Oa03xUSQot+9e/K1tQW6oLe158yc="
        for (header in
            listOf(
                V.COMMIT_MAC_HEADER,
                """mac=:$base64:, ver=1, type="atproto-space-repo"""",
                """type="atproto-space-repo", extra=(a "b";c=1), ver=1;p=?1, mac=:$base64:, more=tok/en:x, flag""",
                """type="atproto-space-repo",ver=1,mac=:${base64.trimEnd('=')}:""",
                """type="other", type="atproto-space-repo", ver=1, mac=:AAAA:, mac=:$base64:""",
            )) {
            assertContentEquals(mac, SpaceMac.parseMacHeader(header, SpaceMac.COMMIT_TYPE).getOrThrow(), header)
        }
    }

    @Test
    fun badMacHeadersAreMalformed() = runTest {
        val base64 = "5MEKSII27AWjDR9Oa03xUSQot+9e/K1tQW6oLe158yc="
        val verifier = commitVerifier()
        for (header in
            listOf(
                V.RECORD_MAC_HEADER,
                """type=atproto-space-repo, ver=1, mac=:$base64:""",
                """type="atproto-space-repo", ver=2, mac=:$base64:""",
                """type="atproto-space-repo", mac=:$base64:""",
                """type="atproto-space-repo", ver=1""",
                """type="atproto-space-repo", ver=1, mac="$base64"""",
                """type="atproto-space-repo", ver=1, mac=:AAECAwQF:""",
                """type="atproto-space-repo", ver=1, mac=:not base64!:""",
                """type="atproto-space-repo", ver=1, mac=:$base64""",
                """type="atproto-space-repo", ver=1, mac=:$base64:,""",
                """Type="atproto-space-repo", ver=1, mac=:$base64:""",
                """type="atproto-space-repo; ver=1, mac=:$base64:""",
                "",
            )) {
            assertIs<SpaceMacResult.Malformed>(verifier.verify(header, V.REV, V.DATA.hexToByteArray()), header)
        }
    }

    @Test
    fun referenceMacsAreValid() = runTest {
        assertEquals(SpaceMacResult.Valid, commitVerifier().verify(V.COMMIT_MAC_HEADER, V.REV, V.DATA.hexToByteArray()))
        assertEquals(SpaceMacResult.Valid, recordVerifier().verify(V.RECORD_MAC_HEADER, Cid(V.CID)))
    }

    @Test
    fun anyChangedInputIsMismatch() = runTest {
        val data = V.DATA.hexToByteArray()
        assertEquals(SpaceMacResult.Mismatch, commitVerifier().verify(V.COMMIT_MAC_HEADER, V.REV, flippedBytes(V.DATA)))
        assertEquals(SpaceMacResult.Mismatch, commitVerifier().verify(V.COMMIT_MAC_HEADER, flipped(V.REV), data))
        assertEquals(SpaceMacResult.Mismatch, commitVerifier(space = flipped(V.SPACE)).verify(V.COMMIT_MAC_HEADER, V.REV, data))
        assertEquals(SpaceMacResult.Mismatch, commitVerifier(repo = flipped(V.REPO)).verify(V.COMMIT_MAC_HEADER, V.REV, data))
        assertEquals(SpaceMacResult.Mismatch, recordVerifier(flipped(V.URI)).verify(V.RECORD_MAC_HEADER, Cid(V.CID)))
        // A CID with one bit of its digest flipped (the last base32 character holds padding bits, so the one before it) is still a valid
        // CIDv1.
        assertEquals(SpaceMacResult.Mismatch, recordVerifier().verify(V.RECORD_MAC_HEADER, Cid(flipped(V.CID, V.CID.length - 2))))
        val otherMac = V.COMMIT_MAC_HEADER.replace("5MEK", "5MEL")
        assertEquals(SpaceMacResult.Mismatch, commitVerifier().verify(otherMac, V.REV, data))
    }

    @Test
    fun noHeaderIsMissing() = runTest {
        assertEquals(SpaceMacResult.Missing, commitVerifier().verify(null, V.REV, V.DATA.hexToByteArray()))
        assertEquals(SpaceMacResult.Missing, recordVerifier().verify(null, Cid(V.CID)))
    }

    @Test
    fun forRecordAgreesAFreshKey() = runTest {
        val first = SpaceMac.forRecord(V.AUTHOR_MULTIKEY, AtUri(V.URI))
        val second = SpaceMac.forRecord(V.AUTHOR_MULTIKEY, AtUri(V.URI))
        assertNotEquals(first.wantMacHeader, second.wantMacHeader)
        // Someone else's secret never validates the reference MAC.
        assertEquals(SpaceMacResult.Mismatch, first.verify(V.RECORD_MAC_HEADER, Cid(V.CID)))
    }
}
