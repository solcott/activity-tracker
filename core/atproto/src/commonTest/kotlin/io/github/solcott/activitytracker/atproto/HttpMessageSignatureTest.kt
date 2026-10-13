package io.github.solcott.activitytracker.atproto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.space.HttpMessageSignature
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Vectors from `@atproto/space`'s `createSpaceSigHeaders` in the PDS image pinned in docker/pds.Dockerfile, with RFC 6979 A.2.5's P-256
 * key. ECDSA here is randomized, so the tests compare the signature base and `Signature-Input` byte for byte, check that the reference
 * signature verifies over our base, and verify our own signature.
 */
class HttpMessageSignatureTest {
    private val key = suspend {
        DpopKey.restore(
            DpopKey.Serialized(
                privateKeyDer = PRIVATE_DER,
                publicKeyRaw = PUBLIC_RAW,
            )
        )
    }

    @Test
    fun credentialRequestMatchesReference() = runTest {
        val headers = HttpMessageSignature.sign(key(), REQUEST_AUTHORIZATION)
        assertEquals(
            """atproto-space=("authorization");keyid="did:key:zDnaepBuvsQ8cpsWrVKw8fbpGpvPeNSjVPTWoq6cRqaYzBKVP"""",
            headers.getValue("Signature-Input"),
        )
        val params = headers.getValue("Signature-Input").removePrefix("atproto-space=")
        val base = HttpMessageSignature.signatureBase(REQUEST_AUTHORIZATION, params, audience = null)
        assertTrue(verifies(base, "aY5NoNbGmjW7+IUAM36BOryTyC4XltZUYPaiFdrpF6YNndHx+Rno243N7kmzjCAE/5EVQZzz/4odcCo2LbzPlg"))
        assertTrue(verifies(base, headers.signatureValue()))
    }

    @Test
    fun credentialedReadMatchesReference() = runTest {
        val headers = HttpMessageSignature.sign(key(), READ_AUTHORIZATION, AUDIENCE)
        assertEquals("""atproto-space=("authorization" "atproto-space-audience")""", headers.getValue("Signature-Input"))
        val base = HttpMessageSignature.signatureBase(READ_AUTHORIZATION, """("authorization" "atproto-space-audience")""", AUDIENCE)
        assertEquals(
            "\"authorization\": $READ_AUTHORIZATION\n\"atproto-space-audience\": did:plc:alice\n" +
                "\"@signature-params\": (\"authorization\" \"atproto-space-audience\")",
            base.decodeToString(),
        )
        assertTrue(verifies(base, "1NQSl+q59pYicSI213ZRMzPjrayVf3pA8OgtnSrwNWdYByj/DzyLmuYit+3XZAN2Ydxn6SAHeN4Aj0FHYM7vFg"))
        assertTrue(verifies(base, headers.signatureValue()))
    }

    @Test
    fun signatureIsUnpaddedStandardBase64() = runTest {
        val value = HttpMessageSignature.sign(key(), READ_AUTHORIZATION, AUDIENCE).signatureValue()
        assertEquals(86, value.length)
        assertTrue(value.none { it == '=' || it == '-' || it == '_' }, value)
    }

    private fun Map<String, String>.signatureValue(): String {
        val header = getValue("Signature")
        assertTrue(header.startsWith("atproto-space=:") && header.endsWith(":"), header)
        return header.removePrefix("atproto-space=:").removeSuffix(":")
    }

    private suspend fun verifies(base: ByteArray, signature: String): Boolean {
        val publicKey =
            CryptographyProvider.Default.get(ECDSA)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(PUBLIC_RAW))
        val bytes = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(signature)
        return publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).tryVerifySignature(base, bytes)
    }

    private companion object {
        /** PKCS#8 DER (base64url) of the same key. */
        const val PRIVATE_DER =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgya-p2EW6dRZrXCFXZ7HWk0" +
                "5Qw9s26JsSe4piKxIPZyGhRANCAARg_tS6JVqdMclh63TGNW1owEm4kjth-mzmaWIuYPKf" +
                "tnkD_hAIuLyZpBrp6VYovGTy8bIMLX6fUXejwpTURiKZ"
        const val PUBLIC_RAW = "BGD-1LolWp0xyWHrdMY1bWjASbiSO2H6bOZpYi5g8p-2eQP-EAi4vJmkGunpVii8ZPLxsgwtfp9Rd6PClNRGIpk"
        const val REQUEST_AUTHORIZATION = "Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJhdHByb3RvLXNwYWNlLWRlbGVnYXRpb24rand0In0.e30.c2ln"
        const val READ_AUTHORIZATION = "Atproto-Space eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJhdHByb3RvLXNwYWNlLWNyZWRlbnRpYWwrand0In0.e30.c2ln"
        val AUDIENCE = Did("did:plc:alice")
    }
}
