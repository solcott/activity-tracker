package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DidDocumentTest {
    private val kem = "zDnaeQa8zprPhHA7Yuxcgc2Uh6XNQgaKjmFZE4EaA9fk5svmJ"

    private fun document(vararg methods: VerificationMethod) = DidDocument(id = "did:plc:alice", verificationMethod = methods.toList())

    private fun method(id: String, key: String) = VerificationMethod(id, "Multikey", "did:plc:alice", key)

    @Test
    fun findsKemKeyByFullId() {
        val doc = document(method("did:plc:alice#atproto", "zQ3sh"), method("did:plc:alice#atproto_kem", kem))
        assertEquals(kem, doc.kemKey)
        assertEquals("zQ3sh", doc.signingKey)
    }

    @Test
    fun findsKemKeyByFragment() {
        assertEquals(kem, document(method("#atproto_kem", kem)).kemKey)
    }

    @Test
    fun noKemKeyIsNull() {
        assertNull(document(method("did:plc:alice#atproto", "zQ3sh")).kemKey)
    }
}
