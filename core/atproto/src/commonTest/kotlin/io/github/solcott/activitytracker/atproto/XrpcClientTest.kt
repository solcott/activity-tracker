package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.atproto.xrpc.atprotoProxy
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.atproto.CreateReport
import io.github.solcott.activitytracker.lexicon.atproto.ReasonType
import io.github.solcott.activitytracker.lexicon.atproto.ReportSubject
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
        assertEquals(HttpStatusCode.Unauthorized.value, e.status)
        assertEquals("AuthRequired", e.error)
    }

    @Test
    fun queryBytesReturnsBodyAndContentType() = runTest {
        val bytes = byteArrayOf(0, 1, 2, -1)
        network.on("appview.test/xrpc/com.atproto.space.getBlob") {
            respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val output = client.queryBytes(Space.GetBlob.method, blobParams)
        assertEquals(bytes.toList(), output.bytes.toList())
        assertEquals("application/octet-stream", output.contentType)
        assertEquals("bafkblob", network.requests.single().url.parameters["cid"])
    }

    @Test
    fun queryBytesErrorsSurfaceXrpcErrorBody() = runTest {
        network.on("appview.test/xrpc/com.atproto.space.getBlob") {
            respondJson("""{"error":"BlobNotFound","message":"no such blob"}""", HttpStatusCode.BadRequest)
        }
        val e = assertFailsWith<XrpcException> { client.queryBytes(Space.GetBlob.method, blobParams) }
        assertEquals("BlobNotFound", e.error)
    }

    @Test
    fun procedureSendsPerCallHeaders() = runTest {
        network.on("appview.test/xrpc/com.atproto.moderation.createReport") {
            respondJson(
                """{"id":1,"reasonType":"${ReasonType.SPAM}","reportedBy":"did:plc:alice","createdAt":"2026-10-20T10:00:00Z",
                  "subject":{"${'$'}type":"com.atproto.admin.defs#repoRef","did":"did:plc:bob"}}"""
            )
        }
        val labeler = Did("did:plc:labeler")
        val input = CreateReport.Input(ReasonType.SPAM, ReportSubject.Repo(Did("did:plc:bob")))
        client.procedure(CreateReport.method, input, atprotoProxy(labeler, "atproto_labeler"))
        assertEquals("did:plc:labeler#atproto_labeler", network.requests.single().headers["atproto-proxy"])

        // Only that call carries it.
        client.procedure(CreateReport.method, input)
        assertEquals(null, network.requests.last().headers["atproto-proxy"])
    }

    private val blobParams =
        Space.GetBlob.Params(SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "self"), Did("did:plc:alice"), Cid("bafkblob"))
}
