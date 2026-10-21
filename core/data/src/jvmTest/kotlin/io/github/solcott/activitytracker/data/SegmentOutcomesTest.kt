package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class SegmentOutcomesTest {
    private val uri = AtUri("at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill")

    private val segmentJson =
        """{"segment":{"uri":"$uri","cid":"bafyseg","creator":{"did":"did:plc:carol","handle":"carol.test"},""" +
            """"record":{"name":"Hill","sport":"run","line":"_p~iF~ps|U","source":"athlete",""" +
            """"license":"CC0-1.0","createdAt":"2026-10-01T00:00:00Z"},"distanceMeters":1000,"status":"active"}}"""

    /** How the AppView answers `getSegment`. */
    private var answer = "ok"

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_SEGMENT}" ->
                        when (answer) {
                            "offline" -> throw IOException("Connection refused")
                            "missing" -> respond(NOT_FOUND, HttpStatusCode.BadRequest, json)
                            else -> respond(segmentJson, headers = json)
                        }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        )

    private suspend fun segments(): SegmentRepository {
        val (_, _, clients) = signedInTestSession(http, did = Did("did:plc:alice"))
        return SegmentRepository(clients)
    }

    @Test
    fun loadsThenAnswersWithTheSegment() = runTest {
        val outcomes = segments().segmentOutcome(uri).toList()
        assertEquals(Outcome.Loading, outcomes.first())
        val data = assertIs<Outcome.Data<*>>(outcomes.last())
        assertEquals(Origin.Network, data.origin)
    }

    @Test
    fun anXrpcErrorKeepsItsName() = runTest {
        answer = "missing"
        assertEquals(
            Outcome.Error(DataError.Api(listOf("Segment not found"), code = "NotFound"), Origin.Network),
            segments().segmentOutcome(uri).toList().last(),
        )
    }

    @Test
    fun noConnectionIsNetwork() = runTest {
        answer = "offline"
        assertEquals(Outcome.Error(DataError.Network, Origin.Network), segments().segmentOutcome(uri).toList().last())
    }
}

private const val NOT_FOUND = """{"error":"NotFound","message":"Segment not found"}"""
