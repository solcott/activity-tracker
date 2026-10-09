package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class OwnActivitiesByStartTest {
    private val did = Did("did:plc:alice")
    private val requests = mutableListOf<Map<String, List<String>>>()

    private fun record(start: String, sport: String = Sport.DIVE) =
        ActivityRecord(sport, Instant.parse(start), 2700, Instant.parse("2026-10-01T00:00:00Z"))

    private fun view(rkey: String, start: String) =
        ActivityView(
            uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = record(start),
            kudosCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    /** Two pages, the second after the cursor the first gives. */
    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> Unit
                    else -> return@MockEngine respondError(HttpStatusCode.NotFound)
                }
                requests += request.url.parameters.entries().associate { it.key to it.value }
                val output =
                    if (request.url.parameters["cursor"] == null) {
                        GetAuthorActivitiesByStart.Output(listOf(view("3kd1", "2024-05-12T16:00:00Z")), cursor = "next")
                    } else {
                        GetAuthorActivitiesByStart.Output(listOf(view("3kd3", "2024-05-12T20:00:00Z")))
                    }
                respond(AtJson.encodeToString(GetAuthorActivitiesByStart.Output.serializer(), output), headers = json)
            }
        )

    @Test
    fun readsEveryPageAndWhatTheOutboxHolds() = runTest {
        val (db, _, clients) = signedInTestSession(http, did = did)
        val writer = OutboxWriter(db, clients, Clock.System)
        // Published moments ago: the AppView hasn't indexed these yet.
        writer.put(Nsid.FEED_ACTIVITY, "3kd2", ActivityRecord.serializer(), record("2024-05-12T18:00:00Z"))
        writer.put(Nsid.FEED_ACTIVITY, "3kd9", ActivityRecord.serializer(), record("2024-06-01T10:00:00Z"))
        writer.put(Nsid.FEED_ACTIVITY, "3kr1", ActivityRecord.serializer(), record("2024-05-12T07:00:00Z", Sport.RUN))
        val feed = FeedRepository(db, clients, LocalKudos(), Logger)

        val own = feed.ownActivitiesStartedBetween(Instant.parse("2024-05-12T00:00:00Z"), Instant.parse("2024-05-13T00:00:00Z"), Sport.DIVE)

        assertEquals(listOf("3kd1", "3kd2", "3kd3"), own.map { it.uri.rkey }, "oldest first, outside the range or another sport left out")
        assertEquals(listOf(Cid("bafy3kd1"), null, Cid("bafy3kd3")), own.map { it.cid })
        assertEquals(listOf(null, "next"), requests.map { it["cursor"]?.single() })
        assertEquals(listOf(Sport.DIVE), requests.first()["sport"])
        assertEquals(listOf(did.value), requests.first()["actor"])
    }
}
