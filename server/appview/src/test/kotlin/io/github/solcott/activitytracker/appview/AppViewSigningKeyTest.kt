package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.Multikey
import io.github.solcott.activitytracker.appview.auth.SigningKey
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.signers.ECDSASigner

/** A fixed P-256 private scalar for tests (not a real key). */
const val TEST_SIGNING_KEY = "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721"

class AppViewSigningKeyTest {
    private val key = AppViewSigningKey(TEST_SIGNING_KEY)

    @Test
    fun publishedKeyVerifiesSignatures() {
        val published = Multikey.decode(key.publicMultibase)
        assertEquals(SigningKey.Curve.P256, published.curve)
        assertEquals("ES256", key.jwtAlg)
        val data = "header.payload".encodeToByteArray()
        val sig = key.sign(data)
        assertEquals(64, sig.size)
        val r = BigInteger(1, sig.copyOfRange(0, 32))
        val s = BigInteger(1, sig.copyOfRange(32, 64))
        // @atproto/crypto rejects high-S signatures.
        assertTrue(s <= Multikey.domain(SigningKey.Curve.P256).n.shiftRight(1))
        val verifier = ECDSASigner().apply { init(false, published.params) }
        assertTrue(verifier.verifySignature(MessageDigest.getInstance("SHA-256").digest(data), r, s))
        // RFC 6979: the same input signs the same way.
        assertContentEquals(sig, key.sign(data))
    }

    @Test
    fun delegationTokensVerifyAgainstThePublishedKey() = runBlocking {
        val space = SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "followers")
        val token = SignedDelegationTokens(Did("did:web:appview.test"), key.jwtAlg, key::sign).token(space)
        val (header, payload, signature) = token.split('.')
        val sig = Base64.getUrlDecoder().decode(signature)
        val published = Multikey.decode(key.publicMultibase)
        val verifier = ECDSASigner().apply { init(false, published.params) }
        val digest = MessageDigest.getInstance("SHA-256").digest("$header.$payload".encodeToByteArray())
        assertTrue(verifier.verifySignature(digest, BigInteger(1, sig.copyOfRange(0, 32)), BigInteger(1, sig.copyOfRange(32, 64))))
        val claims = AtJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload))).jsonObject
        assertEquals("did:web:appview.test", claims["iss"]?.jsonPrimitive?.content)
        assertEquals("did:plc:alice#atproto_pds", claims["aud"]?.jsonPrimitive?.content)
    }

    @Test
    fun rejectsMalformedKeys() {
        assertFailsWith<IllegalArgumentException> { AppViewSigningKey("abc") }
        assertFailsWith<IllegalArgumentException> { AppViewSigningKey("0".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { AppViewSigningKey("f".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { AppViewSigningKey("z".repeat(64)) }
    }
}
