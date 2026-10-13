package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.io.encoding.Base64

/**
 * The RFC 9421 HTTP message signature atproto Spaces puts on credential requests and credentialed reads, ported from `@atproto/space`'s
 * `http-signature.ts` in the PDS image pinned in docker/pds.Dockerfile. It covers only header fields, so it's built by hand rather than by
 * a general RFC 9421 implementation:
 * - a credential request signs `authorization` and names the signing key (`keyid`, a P-256 `did:key`);
 * - a credentialed read signs `authorization` and `atproto-space-audience`, with the key the credential binds and no `keyid`.
 */
internal object HttpMessageSignature {
    const val LABEL = "atproto-space"
    const val SIGNATURE_INPUT = "Signature-Input"
    const val SIGNATURE = "Signature"
    const val AUDIENCE = "Atproto-Space-Audience"

    /** Standard alphabet without padding, as `@atproto/lex-data`'s `toBase64` writes the signature. */
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /**
     * Signs a request carrying [authorization] (and, for a credentialed read, [audience]) and returns the `Signature-Input` and `Signature`
     * header values.
     */
    suspend fun sign(key: DpopKey, authorization: String, audience: Did? = null): Map<String, String> {
        val params = signatureParams(key.didKey, audience)
        val signature = key.sign(signatureBase(authorization, params, audience))
        return mapOf(SIGNATURE_INPUT to "$LABEL=$params", SIGNATURE to "$LABEL=:${base64.encode(signature)}:")
    }

    fun signatureParams(didKey: String, audience: Did?): String =
        if (audience == null) """("authorization");keyid="$didKey"""" else """("authorization" "atproto-space-audience")"""

    fun signatureBase(authorization: String, params: String, audience: Did?): ByteArray = buildList {
        add(""""authorization": ${authorization.trim()}""")
        if (audience != null) add(""""atproto-space-audience": ${audience.value.trim()}""")
        add(""""@signature-params": $params""")
    }
        .joinToString("\n")
        .encodeToByteArray()
}
