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
 * Vectors from `@atproto/space`'s `createSpaceSigHeaders` in the PDS image pinned in docker/pds.Dockerfile (Oct 22 alpha), with RFC 6979
 * A.2.5's P-256 key. ECDSA here is randomized, so the tests compare the signature base and `Signature-Input` byte for byte, check that the
 * reference signature verifies over our base, and verify our own signature.
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
    fun credentialedReadMatchesReference() = runTest {
        val headers = HttpMessageSignature.sign(key(), AUTHORIZATION, AUDIENCE)
        assertEquals(
            """atproto-space-credential=("authorization" "atproto-space-audience");tag="atproto-space-credential"""",
            headers.getValue("Signature-Input"),
        )
        val base = HttpMessageSignature.signatureBase(AUTHORIZATION, AUDIENCE)
        assertEquals(
            "\"authorization\": $AUTHORIZATION\n\"atproto-space-audience\": did:plc:alice\n" +
                "\"@signature-params\": (\"authorization\" \"atproto-space-audience\");tag=\"atproto-space-credential\"",
            base.decodeToString(),
        )
        assertTrue(verifies(base, REFERENCE_SIGNATURE))
        assertTrue(verifies(base, headers.signatureValue()))
    }

    @Test
    fun signatureIsPaddedStandardBase64() = runTest {
        val value = HttpMessageSignature.sign(key(), AUTHORIZATION, AUDIENCE).signatureValue()
        assertEquals(88, value.length)
        assertTrue(value.endsWith("=="), value)
        assertTrue(value.none { it == '-' || it == '_' }, value)
    }

    private fun Map<String, String>.signatureValue(): String {
        val header = getValue("Signature")
        assertTrue(header.startsWith("atproto-space-credential=:") && header.endsWith(":"), header)
        return header.removePrefix("atproto-space-credential=:").removeSuffix(":")
    }

    private suspend fun verifies(base: ByteArray, signature: String): Boolean {
        val publicKey =
            CryptographyProvider.Default.get(ECDSA)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(PUBLIC_RAW))
        val bytes = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(signature)
        return publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).tryVerifySignature(base, bytes)
    }

    private companion object {
        /** PKCS#8 DER (base64url) of the same key. */
        const val PRIVATE_DER =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgya-p2EW6dRZrXCFXZ7HWk0" +
                "5Qw9s26JsSe4piKxIPZyGhRANCAARg_tS6JVqdMclh63TGNW1owEm4kjth-mzmaWIuYPKf" +
                "tnkD_hAIuLyZpBrp6VYovGTy8bIMLX6fUXejwpTURiKZ"
        const val PUBLIC_RAW = "BGD-1LolWp0xyWHrdMY1bWjASbiSO2H6bOZpYi5g8p-2eQP-EAi4vJmkGunpVii8ZPLxsgwtfp9Rd6PClNRGIpk"
        const val AUTHORIZATION =
            "Atproto-Space-Credential eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJhdHByb3RvLXNwYWNlLWNyZWRlbnRpYWwrand0In0.e30.c2ln"
        const val REFERENCE_SIGNATURE = "Y2UugAxIVXXhXq7eHe1GZ3rTkfXWSq2dTanu2elpeAYSc2+Pn/aydV1TjOR9Vm+iLv8MEi+m2g7H/d7gIdKnZw=="
        val AUDIENCE = Did("did:plc:alice")
    }
}
