package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class XrpcClientTest {
    private val network = MockNetwork()
    private val client = XrpcClient(network.client, "http://appview.test")

    @Test
    fun queryEncodesParamsAndDecodesOutput() = runTest {
        network.on("appview.test/xrpc/app.activitytracker.feed.getTimeline") { respondJson("""{"activities":[],"cursor":"next"}""") }
        val output = client.query(GetTimeline.method, GetTimeline.Params(limit = 10))
        assertEquals("next", output.cursor)
        assertEquals(
            "10",
            network.requests.single().url.parameters["limit"],
        )
        assertEquals(
            null,
            network.requests.single().url.parameters["cursor"],
        )
    }

    @Test
    fun errorsSurfaceXrpcErrorBody() = runTest {
        network.on("appview.test/xrpc/app.activitytracker.feed.getTimeline") {
            respondJson(
                """{"error":"AuthRequired","message":"sign in"}""",
                HttpStatusCode.Unauthorized,
            )
        }
        val e = assertFailsWith<XrpcException> { client.query(GetTimeline.method, GetTimeline.Params()) }
        assertEquals(401, e.status)
        assertEquals("AuthRequired", e.error)
    }
}
