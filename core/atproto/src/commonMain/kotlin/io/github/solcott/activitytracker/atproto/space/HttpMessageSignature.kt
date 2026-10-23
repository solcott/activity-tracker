package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.io.encoding.Base64

/**
 * The RFC 9421 HTTP message signature atproto Spaces puts on credentialed reads, ported from `@atproto/space`'s `http-signature.ts` in the
 * PDS image pinned in docker/pds.Dockerfile. It covers only header fields, so it's built by hand rather than by a general RFC 9421
 * implementation: `authorization` and `atproto-space-audience`, tagged `atproto-space-credential`, signed with the key the credential binds
 * and naming no `keyid`. Credential requests carry no signature.
 */
internal object HttpMessageSignature {
    const val LABEL = "atproto-space-credential"
    const val TAG = "atproto-space-credential"
    const val SIGNATURE_INPUT = "Signature-Input"
    const val SIGNATURE = "Signature"
    const val AUDIENCE = "Atproto-Space-Audience"

    /** RFC 8941 byte sequences are padded standard base64, as `@atproto/space`'s `toPaddedBase64` writes the signature. */
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT)

    const val PARAMS = """("authorization" "atproto-space-audience");tag="$TAG""""

    /**
     * Signs a credentialed read carrying [authorization] for [audience] and returns the `Signature-Input` and `Signature` header values.
     */
    suspend fun sign(key: DpopKey, authorization: String, audience: Did): Map<String, String> {
        val signature = key.sign(signatureBase(authorization, audience))
        return mapOf(SIGNATURE_INPUT to "$LABEL=$PARAMS", SIGNATURE to "$LABEL=:${base64.encode(signature)}:")
    }

    fun signatureBase(authorization: String, audience: Did): ByteArray =
        listOf(
                """"authorization": ${authorization.trim()}""",
                """"atproto-space-audience": ${audience.value.trim()}""",
                """"@signature-params": $PARAMS""",
            )
            .joinToString("\n")
            .encodeToByteArray()
}
