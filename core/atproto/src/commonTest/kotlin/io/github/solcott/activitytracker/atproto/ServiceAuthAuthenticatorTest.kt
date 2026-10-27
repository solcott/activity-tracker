package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.xrpc.ServiceAuthAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ServiceAuthAuthenticatorTest {
    private val network = MockNetwork()
    private val pdsNow = Instant.fromEpochSeconds(1_800_000_000)
    private val device = DeviceClock(pdsNow)
    private var minted = 0

    /** What the PDS answers: an unsigned JWT timed by its own clock (60 s, its default), unless set. */
    private var mint: () -> String = { jwt(iat = pdsNow.epochSeconds, exp = pdsNow.epochSeconds + 60) }

    private val appView =
        XrpcClient(
            network.client,
            "http://appview.test",
            ServiceAuthAuthenticator({ XrpcClient(network.client, "http://pds.test") }, "did:web:appview.test", device),
        )

    init {
        network.on("pds.test/xrpc/com.atproto.server.getServiceAuth") { request ->
            // Like a real PDS, it refuses an expiry already past by its own clock.
            val exp = request.url.parameters["exp"]?.toLong()
            if (exp != null && exp <= pdsNow.epochSeconds)
                return@on respondJson("""{"error":"BadExpiration","message":"expiration is in past"}""", HttpStatusCode.BadRequest)
            minted++
            respondJson("""{"token":"${mint()}"}""")
        }
        network.on("appview.test/xrpc/${GetTimeline.method.nsid}") { respondJson("""{"activities":[]}""") }
    }

    @Test
    fun aSlowDeviceClockStillGetsAToken() = runTest {
        device.offset = (-2).minutes

        appView.query(GetTimeline.method, GetTimeline.Params())

        val ask = network.requests.first { it.url.host == "pds.test" }
        assertNull(ask.url.parameters["exp"], "the PDS sets the expiry")
        assertEquals("did:web:appview.test", ask.url.parameters["aud"])
        assertEquals(GetTimeline.method.nsid, ask.url.parameters["lxm"])
        assertEquals("Bearer ${mint()}", appViewCalls().single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aFastDeviceClockStillReusesTheToken() = runTest {
        device.offset = 2.minutes

        appView.query(GetTimeline.method, GetTimeline.Params())
        device.offset += 10.seconds
        appView.query(GetTimeline.method, GetTimeline.Params())

        assertEquals(1, minted)
        assertEquals(2, appViewCalls().size)
    }

    @Test
    fun aTokenIsReplacedNearTheEndOfItsLifetime() = runTest {
        appView.query(GetTimeline.method, GetTimeline.Params())
        device.offset += 45.seconds
        appView.query(GetTimeline.method, GetTimeline.Params())
        assertEquals(1, minted, "still 15 s left")

        device.offset += 10.seconds
        appView.query(GetTimeline.method, GetTimeline.Params())
        assertEquals(2, minted, "within 10 s of its end")
    }

    @Test
    fun aTokenThatIsNotAJwtIsSentOnce() = runTest {
        mint = { "service-jwt" }

        appView.query(GetTimeline.method, GetTimeline.Params())
        appView.query(GetTimeline.method, GetTimeline.Params())

        assertEquals(2, minted)
        assertEquals(listOf("Bearer service-jwt", "Bearer service-jwt"), appViewCalls().map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun anUnauthorizedAnswerDropsTheToken() = runTest {
        var refuse = true
        network.on("appview.test/xrpc/${GetTimeline.method.nsid}") {
            if (refuse) {
                refuse = false
                respondJson("""{"error":"AuthRequired"}""", HttpStatusCode.Unauthorized)
            } else respondJson("""{"activities":[]}""")
        }

        appView.query(GetTimeline.method, GetTimeline.Params())

        assertEquals(2, minted, "the retry mints a new token")
    }

    @Test
    fun anUnauthorizedAnswerKeepsOtherMethodsTokens() = runTest {
        appView.query(GetTimeline.method, GetTimeline.Params())
        network.on("appview.test/xrpc/${GetProfile.method.nsid}") {
            respondJson("""{"error":"AuthRequired"}""", HttpStatusCode.Unauthorized)
        }
        runCatching { appView.query(GetProfile.method, GetProfile.Params("did:plc:bob")) }
        val mintedBefore = minted

        appView.query(GetTimeline.method, GetTimeline.Params())
        assertEquals(mintedBefore, minted, "the timeline's token is still reused")
    }

    private fun appViewCalls() = network.requests.filter { it.url.host == "appview.test" }

    private class DeviceClock(private val base: Instant) : Clock {
        var offset: Duration = Duration.ZERO

        override fun now(): Instant = base + offset
    }

    private companion object {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun jwt(iat: Long, exp: Long): String {
            val header = b64.encode("""{"alg":"ES256K","typ":"JWT"}""".encodeToByteArray())
            val payload =
                b64.encode(
                    """{"iss":"did:plc:alice","aud":"did:web:appview.test","lxm":"${GetTimeline.method.nsid}","iat":$iat,"exp":$exp}"""
                        .encodeToByteArray()
                )
            return "$header.$payload.c2ln"
        }
    }
}
