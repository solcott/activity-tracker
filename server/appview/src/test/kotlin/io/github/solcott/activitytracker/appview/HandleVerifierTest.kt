package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.index.HandleCheck
import io.github.solcott.activitytracker.appview.index.HandleVerifier
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** identity/handle-verification: a claimed handle counts only when it resolves back to the DID. */
class HandleVerifierTest {
    private val identities = FakeIdentities()
    private val verifier = HandleVerifier(identities.resolver)
    private val alice = "did:plc:alice"

    private suspend fun check() = verifier.check(Did(alice))

    @Test
    fun aMatchingHandleIsVerified() = runTest {
        identities.claims[alice] = "Alice.Example.com"
        identities.handles["alice.example.com"] = alice
        assertEquals(HandleCheck.Verified("alice.example.com"), check())
    }

    @Test
    fun aHandleOfAnotherDidIsInvalid() = runTest {
        identities.claims[alice] = "bob.example.com"
        identities.handles["bob.example.com"] = "did:plc:bob"
        assertEquals(HandleCheck.Invalid, check())
    }

    @Test
    fun aHandleThatResolvesToNothingIsInvalid() = runTest {
        identities.claims[alice] = "alice.example.com"
        assertEquals(HandleCheck.Invalid, check())
    }

    @Test
    fun noClaimedHandleIsInvalid() = runTest {
        identities.claims[alice] = null
        assertEquals(HandleCheck.Invalid, check())
        identities.claims[alice] = "not a handle"
        assertEquals(HandleCheck.Invalid, check())
    }

    @Test fun anUnreachableDocumentIsUnreachable() = runTest { assertEquals(HandleCheck.Unreachable, check()) }

    @Test
    fun handleLookupsThatAllFailAreUnreachable() = runTest {
        identities.claims[alice] = "alice.example.com"
        identities.handles["alice.example.com"] = alice
        identities.lookupsDown = true
        assertEquals(HandleCheck.Unreachable, check())
    }
}
