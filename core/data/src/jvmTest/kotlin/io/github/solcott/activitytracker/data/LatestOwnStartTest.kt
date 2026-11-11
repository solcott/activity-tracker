package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Where segment browse opens without a location (segments/segment-browse): the latest own activity with a line. */
class LatestOwnStartTest {
    private val did = Did("did:plc:alice")

    /** Google's example polyline: its first point is (38.5, -120.2). */
    private val sierra = "_p~iF~ps|U_ulLnnqC"

    /** A polyline whose only point is (0, 0). */
    private val nullIsland = "??"

    private fun view(rkey: String, start: String, polyline: String?, author: Did = did) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            record = ActivityRecord(Sport.RUN, Instant.parse(start), 1800, Instant.parse(start), polyline = polyline),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse(start),
        )

    private var served = emptyList<ActivityView>()
    private var failing = false

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" ->
                        if (failing) respondError(HttpStatusCode.InternalServerError)
                        else
                            respond(
                                AtJson.encodeToString(GetAuthorActivities.Output.serializer(), GetAuthorActivities.Output(served)),
                                headers = json,
                            )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

    @Test
    fun theNewestCachedActivityWithALineWins() = runTest {
        val (db, _, clients) = signedInTestSession(http, did = did)
        val cached =
            listOf(
                view("3ka", "2026-10-01T08:00:00Z", nullIsland),
                view("3kb", "2026-10-03T08:00:00Z", sierra),
                view("3kc", "2026-10-04T08:00:00Z", null),
                view("3kd", "2026-10-05T08:00:00Z", nullIsland, author = Did("did:plc:bob")),
            )
        db.feed()
            .upsert(
                cached.mapIndexed { i, view ->
                    FeedItemEntity("timeline", view.uri.value, i, AtJson.encodeToString(ActivityView.serializer(), view))
                },
            )
        served = listOf(view("3kz", "2026-10-06T08:00:00Z", nullIsland))

        assertEquals(GeoPoint(38.5, -120.2), FeedRepository(db, clients, LocalLikes(), Logger).latestOwnStart())
    }

    @Test
    fun withNothingCachedTheAppViewIsAsked() = runTest {
        val (db, _, clients) = signedInTestSession(http, did = did)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        served = listOf(view("3ka", "2026-10-01T08:00:00Z", null), view("3kb", "2026-09-01T08:00:00Z", sierra))
        assertEquals(GeoPoint(38.5, -120.2), feed.latestOwnStart())

        served = listOf(view("3ka", "2026-10-01T08:00:00Z", null))
        assertNull(feed.latestOwnStart(), "no activity with a line")
        failing = true
        assertNull(feed.latestOwnStart(), "the AppView can't be read")
    }
}
