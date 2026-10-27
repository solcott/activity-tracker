package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthException
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** auth/oauth-session: the redirect's `iss` is checked, and required when the authorization server advertises it (RFC 9207). */
class OAuthCallbackIssuerTest {
    private val did = "did:plc:alice"
    private val issuer = "http://pds.test"

    private fun network(advertisesIss: Boolean?) =
        MockNetwork().apply {
            on("pds.test/xrpc/com.atproto.identity.resolveHandle") { respondJson("""{"did":"$did"}""") }
            on("plc.test/$did") {
                respondJson(
                    """{"id":"$did","alsoKnownAs":["at://alice.test"],
                   "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"$issuer"}]}"""
                )
            }
            on("pds.test/.well-known/oauth-protected-resource") {
                respondJson("""{"resource":"$issuer","authorization_servers":["$issuer"]}""")
            }
            val flag = advertisesIss?.let { ""","authorization_response_iss_parameter_supported":$it""" }.orEmpty()
            on("pds.test/.well-known/oauth-authorization-server") {
                respondJson(
                    """{"issuer":"$issuer","authorization_endpoint":"$issuer/oauth/authorize",
                   "token_endpoint":"$issuer/oauth/token","pushed_authorization_request_endpoint":"$issuer/oauth/par"$flag}"""
                )
            }
            on("pds.test/oauth/par") { respondJson("""{"request_uri":"urn:req:1","expires_in":60}""") }
            on("pds.test/oauth/token") {
                respondJson("""{"access_token":"at1","token_type":"DPoP","refresh_token":"rt1","scope":"atproto","sub":"$did"}""")
            }
        }

    private fun MockNetwork.oauth() =
        OAuthClient(
            client,
            IdentityResolver(client, "http://plc.test", "http://pds.test"),
            OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
        )

    private fun MockNetwork.tokenRequests() = requests.count { it.url.encodedPath == "/oauth/token" }

    @Test
    fun aMissingIssuerIsRefusedWhenTheServerAdvertisesIt() = runTest {
        val network = network(advertisesIss = true)
        val oauth = network.oauth()
        val pending = oauth.startAuthorization("alice.test").pending
        assertTrue(pending.issRequired)

        val error =
            assertFailsWith<OAuthException> { oauth.completeAuthorization(pending, mapOf("code" to "c1", "state" to pending.state)) }
        assertEquals("Missing issuer", error.message)
        assertEquals(0, network.tokenRequests())
    }

    @Test
    fun theAdvertisedIssuerSignsIn() = runTest {
        val network = network(advertisesIss = true)
        val oauth = network.oauth()
        val pending = oauth.startAuthorization("alice.test").pending

        val session = oauth.completeAuthorization(pending, mapOf("code" to "c1", "state" to pending.state, "iss" to issuer))
        assertEquals(Did(did), session.did)
    }

    @Test
    fun aMissingIssuerIsAcceptedWhenTheServerDoesNotAdvertiseIt() = runTest {
        for (flag in listOf(false, null)) {
            val network = network(advertisesIss = flag)
            val oauth = network.oauth()
            val pending = oauth.startAuthorization("alice.test").pending
            assertFalse(pending.issRequired)

            val session = oauth.completeAuthorization(pending, mapOf("code" to "c1", "state" to pending.state))
            assertEquals(Did(did), session.did)
        }
    }

    @Test
    fun anotherIssuerIsRefusedEitherWay() = runTest {
        for (flag in listOf(true, false)) {
            val network = network(advertisesIss = flag)
            val oauth = network.oauth()
            val pending = oauth.startAuthorization("alice.test").pending

            val error =
                assertFailsWith<OAuthException> {
                    oauth.completeAuthorization(pending, mapOf("code" to "c1", "state" to pending.state, "iss" to "https://evil.test"))
                }
            assertEquals("Issuer mismatch", error.message)
            assertEquals(0, network.tokenRequests())
        }
    }

    @Test
    fun aPendingAuthorizationFromAnOlderBuildDoesNotRequireTheIssuer() = runTest {
        val pending = network(advertisesIss = true).oauth().startAuthorization("alice.test").pending
        val old = JsonObject(AtJson.parseToJsonElement(pending.encode()).jsonObject - "issRequired").toString()

        assertFalse(PendingAuthorization.decode(old).issRequired)
    }
}
