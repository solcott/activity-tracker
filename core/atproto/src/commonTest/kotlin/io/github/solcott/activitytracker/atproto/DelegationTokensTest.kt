package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.jwtPayload
import io.github.solcott.activitytracker.atproto.space.DelegationTokenSource
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.content.OutgoingContent
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class DelegationTokensTest {
    private val f = SpaceFixtures()
    private val appView = Did("did:web:appview.test")
    private val signed = mutableListOf<ByteArray>()
    private val tokens =
        SignedDelegationTokens(
            appView,
            "ES256",
            { input ->
                signed += input
                byteArrayOf(1, 2, 3)
            },
            f.clock,
        )

    @Test
    fun signedTokenIsAnAtprotoSpaceDelegation() = runTest {
        val token = tokens.token(f.aliceSpace)
        val (header, payload, signature) = token.split('.')
        val headerJson = AtJson.parseToJsonElement(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(header).decodeToString())
        assertEquals("ES256", headerJson.jsonObject["alg"]?.jsonPrimitive?.content)
        assertEquals("atproto-space-delegation+jwt", headerJson.jsonObject["typ"]?.jsonPrimitive?.content)
        assertEquals("#atproto", headerJson.jsonObject["kid"]?.jsonPrimitive?.content)

        val claims = jwtPayload(token)
        assertEquals(appView.value, claims["iss"]?.jsonPrimitive?.content)
        assertEquals(f.aliceSpace.value, claims["sub"]?.jsonPrimitive?.content)
        assertEquals("${f.alice.value}#atproto_pds", claims["aud"]?.jsonPrimitive?.content)
        assertEquals(f.clock.now.epochSeconds, claims["iat"]?.jsonPrimitive?.long)
        assertEquals(f.clock.now.epochSeconds + 60, claims["exp"]?.jsonPrimitive?.long)
        assertEquals(32, claims["jti"]?.jsonPrimitive?.content?.length)

        assertEquals("$header.$payload", signed.single().decodeToString())
        assertEquals("AQID", signature)
        assertNotEquals(claims["jti"], jwtPayload(tokens.token(f.aliceSpace))["jti"], "the PDS refuses a jti twice")
    }

    @Test
    fun credentialsExchangeATokenFromAnySource() = runTest {
        val key = DpopKey.generate()
        val source = DelegationTokenSource { space -> "own-token-for-${space.skey}" }
        val credential = SpaceCredentials(source, f.network.client, f.identity, { key }, f.clock).get(f.aliceSpace)
        assertTrue(credential.token.isNotEmpty())
        assertEquals(emptyList(), f.requestsTo("pds.test/xrpc/com.atproto.space.getDelegationToken"), "no viewer PDS involved")
        val exchange = f.requestsTo("alicepds.test/xrpc/com.atproto.space.getSpaceCredential").single()
        val body = (exchange.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertTrue(""""delegationToken":"own-token-for-mutuals"""" in body, body)
        assertTrue(""""credentialKid":"${key.didKey}"""" in body, body)
    }
}
