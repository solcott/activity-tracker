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
import io.github.solcott.activitytracker.lexicon.app.GetImportBatch
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SportCount
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The timeline's import cards (`feed/import-cards`) as the client caches and shows them. */
class TimelineCardsTest {
    private val did = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val batch = "3m5abcdefghij"

    private fun view(author: Did, rkey: String) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(author, Handle("${author.value.last()}.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    private val card =
        ImportCardView(
            author = ProfileViewBasic(bob, Handle("b.test")),
            batch = batch,
            count = 40,
            earliestStartedAt = Instant.parse("2019-03-02T08:00:00Z"),
            latestStartedAt = Instant.parse("2026-01-01T08:00:00Z"),
            sports = listOf(SportCount("ride", 40)),
            postedAt = Instant.parse("2026-03-06T09:00:00Z"),
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" -> {
                        val output =
                            if (request.url.parameters["type"] != null) GetTimeline.Output.ofActivities(listOf(view(bob, "3kb")))
                            else
                                GetTimeline.Output(
                                    listOf(
                                        TimelineItem.Activity(view(did, "3ka")),
                                        TimelineItem.ImportCard(card),
                                        TimelineItem.Activity(view(bob, "3kb")),
                                    ),
                                )
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), output), headers = json)
                    }
                    "/xrpc/${Nsid.FEED_GET_IMPORT_BATCH}" -> {
                        assertEquals(batch, request.url.parameters["batch"])
                        val output = GetImportBatch.Output(listOf(view(bob, "3kh1"), view(bob, "3kh0")), cursor = "next")
                        respond(AtJson.encodeToString(GetImportBatch.Output.serializer(), output), headers = json)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, LocalLikes(), Logger)
    }

    private fun List<TimelineItem>.keys() = map {
        when (it) {
            is TimelineItem.Activity -> it.view.uri.rkey
            is TimelineItem.ImportCard -> "card"
            is TimelineItem.Post -> "post:${it.post.uri.rkey}"
            is TimelineItem.Repost -> "repost:${it.reposted.post.uri.rkey}"
            is TimelineItem.Unknown -> "unknown"
        }
    }

    @Test
    fun cardsAreCachedInTheirPlace() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        assertEquals(listOf("3ka", "card", "3kb"), feed.timelineItems().first().keys())
        assertEquals(card, (feed.timelineItems().first()[1] as TimelineItem.ImportCard).card)
        assertEquals(listOf("3ka", "3kb"), feed.timeline().first().map { it.uri.rkey }, "the activities alone")
    }

    @Test
    fun aMuteOrBlockDropsTheCard() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        feed.localBlocks.mute(bob, AtUri.of(did, Nsid.GRAPH_MUTE, bob.value))
        assertEquals(listOf("3ka"), feed.timelineItems().first().keys())
        feed.localBlocks.restoreMute(bob, null)
        feed.localBlocks.block(bob, AtUri.of(did, Nsid.GRAPH_BLOCK, bob.value))
        assertEquals(listOf("3ka"), feed.timelineItems().first().keys())
    }

    @Test
    fun aFilteredTimelineIsCachedApart() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        val rides = ActivityFilter(setOf(ActivityType("ride")))
        feed.refreshTimeline(rides)
        assertEquals(listOf("3kb"), feed.timelineItems(rides).first().keys())
        assertEquals(listOf("3ka", "card", "3kb"), feed.timelineItems().first().keys())
    }

    @Test
    fun aBatchPagesFromTheAppView() = runTest {
        val page = assertIs<Outcome.Data<Page<ActivityView, Unit>>>(feed().importBatchPage(bob.value, batch, null)).data
        assertEquals(listOf("3kh1", "3kh0"), page.items.map { it.uri.rkey })
        assertEquals("next", page.next)
    }
}
