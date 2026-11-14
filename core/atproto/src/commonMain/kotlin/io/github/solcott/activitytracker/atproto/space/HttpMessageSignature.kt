package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.io.encoding.Base64

/**
 * The RFC 9421 HTTP message signatures atproto Spaces uses, ported from `@atproto/space`'s `http-signature.ts` in the PDS image pinned in
 * docker/pds.Dockerfile. They cover only header fields, so they're built by hand rather than by a general RFC 9421 implementation.
 *
 * A credentialed read is signed ([sign]) over `authorization` and `atproto-space-audience`, tagged `atproto-space-credential`, with the key
 * the credential binds and naming no `keyid`. Hosts find it by its tag under any label, so this keeps its own. Credential requests carry no
 * signature. [find] and [signatureBase] are shared with [SpaceMac], whose MACs are signatures too.
 */
internal object HttpMessageSignature {
    const val LABEL = "atproto-space-credential"
    const val TAG = "atproto-space-credential"
    const val SIGNATURE_INPUT = "Signature-Input"
    const val SIGNATURE = "Signature"
    const val AUDIENCE = "Atproto-Space-Audience"

    /** RFC 8941 byte sequences are padded standard base64, as `@atproto/space`'s `toPaddedBase64` writes the signature. */
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT)

    private val CREDENTIAL_COMPONENTS = listOf(component("authorization"), component("atproto-space-audience"))

    val PARAMS = StructuredFields.serializeInnerList(SfInnerList(CREDENTIAL_COMPONENTS, mapOf("tag" to SfBare.Str(TAG))))

    /**
     * Signs a credentialed read carrying [authorization] for [audience] and returns the `Signature-Input` and `Signature` header values.
     */
    suspend fun sign(key: DpopKey, authorization: String, audience: Did): Map<String, String> {
        val signature = key.sign(signatureBase(authorization, audience))
        return mapOf(SIGNATURE_INPUT to "$LABEL=$PARAMS", SIGNATURE to "$LABEL=:${base64.encode(signature)}:")
    }

    fun signatureBase(authorization: String, audience: Did): ByteArray =
        signatureBase(CREDENTIAL_COMPONENTS, listOf(authorization, audience.value), PARAMS)

    /** A covered header field's component identifier, e.g. `"authorization"`, with [params] such as `sf`. */
    fun component(name: String, params: Map<String, SfBare> = emptyMap()) = SfItem(SfBare.Str(name), params)

    /**
     * The signature base (RFC 9421 §2.5) of a signature covering [components], whose values are given in order, under the serialized
     * [signatureParams].
     */
    fun signatureBase(components: List<SfItem>, values: List<String>, signatureParams: String): ByteArray =
        (components.mapIndexed { i, c -> "${StructuredFields.serializeItem(c)}: ${values[i].trim()}" } +
                "\"@signature-params\": $signatureParams")
            .joinToString("\n")
            .encodeToByteArray()

    /** What an application requires of the signature it verifies (RFC 9421 §3.2.1), as `@atproto/space`'s `SignatureRequirements`. */
    class Requirements(val tag: String, val components: List<SfItem>, val alg: String, val keyId: String)

    /** A signature [find] accepted: its serialized parameters, which its base ends with, and its bytes. */
    class Found(val signatureParams: String, val signature: ByteArray)

    /**
     * The one signature tagged [Requirements.tag] in a message's `Signature-Input` and `Signature` fields, checked against [required]:
     * labels carry no meaning (RFC 9421 §7.2.5), so the tag identifies it; it must cover exactly the required components, in order; `alg`
     * and `keyid`, when present, must match; other parameters, like `created`, are covered but not read. Throws [IllegalArgumentException]
     * saying what's wrong.
     */
    fun find(inputField: String?, signatureField: String?, required: Requirements): Found {
        requireNotNull(inputField) { "Missing $SIGNATURE_INPUT" }
        requireNotNull(signatureField) { "Missing $SIGNATURE" }
        val inputs = StructuredFields.parseDictionary(inputField)
        val signatures = StructuredFields.parseDictionary(signatureField)
        val tagged = withTag(inputs, required.tag)
        require(tagged.size <= 1) { "More than one signature with tag \"${required.tag}\"" }
        val (label, params) = requireNotNull(tagged.singleOrNull()) { "No signature with tag \"${required.tag}\"" }
        val signature = signatures[label]
        require(signature is SfItem && signature.bare is SfBare.Bytes && signature.params.isEmpty()) {
            "Missing or malformed signature with tag \"${required.tag}\""
        }
        val expected = components(required.components)
        require(components(params.items) == expected) { "Signature must cover exactly $expected, in order" }
        params.params["alg"]?.let { require(it == SfBare.Str(required.alg)) { "Signature algorithm must be ${required.alg}" } }
        params.params["keyid"]?.let { require(it == SfBare.Str(required.keyId)) { "Signature keyid isn't the expected key" } }
        return Found(StructuredFields.serializeInnerList(params), signature.bare.bytes)
    }

    /** A `Signature-Input` or `Accept-Signature` member: its [label], and the components and parameters it names. */
    data class Tagged(val label: String, val params: SfInnerList)

    /** The members of a `Signature-Input` or `Accept-Signature` field tagged [tag]. */
    fun withTag(members: Map<String, SfMember>, tag: String): List<Tagged> = members.mapNotNull { (label, member) ->
        (member as? SfInnerList)?.takeIf { it.params["tag"] == SfBare.Str(tag) }?.let { Tagged(label, it) }
    }

    /** A covered component list without its parameters, e.g. `("authorization" "atproto-space-audience")`. */
    fun components(items: List<SfItem>): String = StructuredFields.serializeInnerList(SfInnerList(items))
}
