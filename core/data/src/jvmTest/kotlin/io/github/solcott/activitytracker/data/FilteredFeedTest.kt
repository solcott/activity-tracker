package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FilteredFeedTest {
    private val did = Did("did:plc:alice")
    private val requests = mutableListOf<Map<String, List<String>>>()

    private fun view(rkey: String, sport: String) =
        ActivityView(
            uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord(sport, Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    /** A timeline of one ride and one run that honors the `type` param, as the AppView does. */
    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" -> Unit
                    else -> return@MockEngine respondError(HttpStatusCode.NotFound)
                }
                requests += request.url.parameters.entries().associate { it.key to it.value }
                val types = request.url.parameters.getAll("type")
                val all = listOf(view("3kride", "ride"), view("3krun", "run"))
                val activities = all.filter { types == null || it.record.sport in types }
                respond(AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output(activities)), headers = json)
            }
        )

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, LocalLikes(), Logger)
    }

    private fun rides(since: String? = null) =
        ActivityFilter(
            setOf(ActivityType("ride")),
            since = since?.let(Instant::parse),
            until = since?.let { Instant.parse("2026-12-31T00:00:00Z") },
        )

    @Test
    fun filteredRefreshSendsParamsAndCachesApart() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        assertEquals(listOf("3kride", "3krun"), feed.timeline().first().map { it.uri.rkey })

        val filter = rides(since = "2026-03-01T07:00:00Z")
        feed.refreshTimeline(filter)

        val params = requests.last()
        assertEquals(listOf("ride"), params["type"])
        assertEquals(listOf("2026-03-01T07:00:00Z"), params["since"])
        assertEquals(listOf("2026-12-31T00:00:00Z"), params["until"])
        assertEquals(null, requests.first()["type"], "the unfiltered refresh sends no filter")
        assertEquals(listOf("3kride"), feed.timeline(filter).first().map { it.uri.rkey })
        assertEquals(listOf("3kride", "3krun"), feed.timeline().first().map { it.uri.rkey }, "the unfiltered cache is untouched")
    }

    @Test
    fun keepsOnlyTheTenMostRecentFilteredFeeds() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        val filters = (1..11).map { rides(since = "2026-03-${it.toString().padStart(2, '0')}T00:00:00Z") }
        filters.forEach { feed.refreshTimeline(it) }

        assertEquals(emptyList(), feed.timeline(filters.first()).first(), "the oldest filtered feed is evicted")
        filters.drop(1).forEach { assertEquals(listOf("3kride"), feed.timeline(it).first().map { v -> v.uri.rkey }) }
        assertEquals(2, feed.timeline().first().size, "the unfiltered feed is never evicted")

        // Refreshing a kept filter makes it the most recent, so the next new one evicts another.
        feed.refreshTimeline(filters[1])
        feed.refreshTimeline(rides(since = "2026-04-01T00:00:00Z"))
        assertEquals(listOf("3kride"), feed.timeline(filters[1]).first().map { it.uri.rkey })
        assertEquals(emptyList(), feed.timeline(filters[2]).first())
    }
}
