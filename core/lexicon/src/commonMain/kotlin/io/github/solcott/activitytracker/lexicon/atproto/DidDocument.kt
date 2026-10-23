package io.github.solcott.activitytracker.lexicon.atproto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The subset of a DID document atproto uses: handle, signing key, and service endpoints. */
@Serializable
data class DidDocument(
    val id: String,
    @SerialName("@context") val context: List<String>? = null,
    val alsoKnownAs: List<String> = emptyList(),
    val verificationMethod: List<VerificationMethod> = emptyList(),
    val service: List<Service> = emptyList(),
) {
    @Serializable
    data class VerificationMethod(
        val id: String,
        val type: String,
        val controller: String,
        val publicKeyMultibase: String? = null,
    )

    @Serializable data class Service(val id: String, val type: String, val serviceEndpoint: String)

    /** `at://handle` entry of `alsoKnownAs`. */
    val handle: String?
        get() = alsoKnownAs.firstOrNull { it.startsWith("at://") }?.removePrefix("at://")

    val pdsEndpoint: String?
        get() = serviceEndpoint("#atproto_pds")

    /** Multibase-encoded repo signing key (`#atproto`). */
    val signingKey: String?
        get() = verificationKey("#atproto")

    /** Multibase-encoded P-256 key (`#atproto_kem`) a Spaces reader agrees a MAC key with. */
    val kemKey: String?
        get() = verificationKey("#atproto_kem")

    /** Multibase-encoded key of the verification method [fragment] (`#atproto_label` for a labeler's label key). */
    fun verificationKey(fragment: String): String? =
        verificationMethod.firstOrNull { it.id == fragment || it.id == "$id$fragment" }?.publicKeyMultibase

    fun serviceEndpoint(fragment: String): String? = service.firstOrNull { it.id == fragment || it.id == "$id$fragment" }?.serviceEndpoint
}
