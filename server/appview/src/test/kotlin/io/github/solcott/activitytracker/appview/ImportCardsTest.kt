package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetImportBatch
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject

/** The timeline's feed position (`feed/timeline-order`) and its import cards (`feed/import-cards`), read back through the API. */
class ImportCardsTest : ModerationTestBase() {
    /** Indexing stamps rows with the wall clock, so every post time here is relative to it. */
    private val now = Clock.System.now()
    private val batch = "3m5abcdefghij"
    private val otherBatch = "3m5bbcdefghij"

    /**
     * [author] posts [rkey]: a ride that started [ago] before [now] and lasted an hour, posted [postedAgo] before [now], at [tier], in
     * [importBatch], with [extra] JSON fields.
     */
    @Suppress("LongParameterList") // What each test varies.
    private fun ride(
        author: String,
        rkey: String,
        ago: Duration,
        postedAgo: Duration = Duration.ZERO,
        tier: String = Visibility.PUBLIC,
        importBatch: String? = null,
        sport: String = "ride",
        extra: String = "",
    ) {
        val json =
            """{"sport":"$sport","startedAt":"${now - ago}","elapsedTime":3600,"createdAt":"${now - postedAgo}"""" +
                importBatch?.let { ""","importBatch":"$it"""" }.orEmpty() +
                extra +
                "}"
        if (tier == Visibility.PUBLIC) {
            commit(author, Nsid.FEED_ACTIVITY, rkey, json)
        } else {
            runBlocking {
                indexer.spaceWrite(
                    SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, tier),
                    Nsid.FEED_ACTIVITY,
                    rkey,
                    "bafy$rkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
        }
    }

    /** Alice imports [count] old rides a minute ago, a year back and a day apart, in [importBatch], each rkey [prefix] + its index. */
    private fun importHistory(count: Int, prefix: String = "3kh", importBatch: String = batch, tier: String = Visibility.PUBLIC) =
        repeat(count) { i ->
            ride(
                alice,
                "$prefix${i.toString().padStart(3, '0')}",
                ago = 365.days - i.days,
                postedAgo = 1.minutes,
                tier = tier,
                importBatch = importBatch,
            )
        }

    private fun interactionsWrite(author: String, collection: String, subject: String) = runBlocking {
        indexer.spaceWrite(
            SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS),
            collection,
            subject,
            "bafy$subject",
            AtJson.parseToJsonElement("""{"subject":"$subject","createdAt":"2026-10-21T09:00:00Z"}""").jsonObject,
        )
    }

    private suspend fun HttpClient.feed(viewer: String, query: String = ""): GetTimeline.Output =
        query(GetTimeline.method, viewer, "?limit=50$query")

    /** The timeline as rkeys, and `card:<batch>` for a card. */
    private suspend fun HttpClient.items(viewer: String, query: String = ""): List<String> =
        feed(viewer, query).feed.map {
            when (it) {
                is TimelineItem.Activity -> it.view.uri.rkey!!
                is TimelineItem.ImportCard -> "card:${it.card.batch}"
                is TimelineItem.Post -> "post:${it.post.uri.rkey}"
                is TimelineItem.Repost -> "repost:${it.reposted.post.uri.rkey}"
                is TimelineItem.Unknown -> "unknown"
            }
        }

    private suspend fun HttpClient.card(viewer: String): ImportCardView = feed(viewer).importCards.single()

    private fun setUp() {
        profile(alice)
        profile(bob)
        follow(bob, alice)
    }

    @Test
    fun recentActivitiesSortByPostTime() = testApplication {
        start()
        setUp()
        ride(alice, "3kb", ago = 2.hours, postedAgo = 1.hours - 1.minutes)
        ride(alice, "3ka", ago = 4.days, postedAgo = 1.hours)
        ride(alice, "3kc", ago = 400.days)
        assertEquals(listOf("3kb", "3ka", "3kc"), client.items(bob), "a late sync surfaces, a 2019 import sinks")
    }

    @Test
    fun oldActivitiesSortByWhenTheyEnded() = testApplication {
        start()
        setUp()
        ride(alice, "3kd", ago = 300.days)
        ride(alice, "3kc", ago = 200.days, postedAgo = 1.minutes)
        ride(alice, "3ke", ago = 100.days, postedAgo = 2.minutes)
        ride(alice, "3kf", ago = 1.hours, postedAgo = 10.minutes)
        assertEquals(listOf("3kf", "3ke", "3kc", "3kd"), client.items(bob), "out of save order, by end time")
    }

    @Test
    fun futurePostTimeIsCapped() = testApplication {
        start()
        setUp()
        ride(alice, "3ka", ago = 1.hours, postedAgo = -30.days)
        // Posted after 3ka was indexed, so above it only if 3ka's future post time was capped.
        ride(alice, "3kb", ago = 30.minutes, postedAgo = now - Clock.System.now())
        assertEquals(listOf("3kb", "3ka"), client.items(bob), "a future createdAt is capped at index time")
    }

    @Test
    fun noElapsedTimeEndsAtTheStart() = testApplication {
        start()
        setUp()
        commit(alice, Nsid.FEED_ACTIVITY, "3ka", """{"sport":"run","startedAt":"${now - 400.days}","elapsedTime":0,"createdAt":"$now"}""")
        ride(alice, "3kb", ago = 400.days - 30.minutes)
        assertEquals(listOf("3kb", "3ka"), client.items(bob))
    }

    @Test
    fun combiningDoesNotResurfaceAnOldActivity() = testApplication {
        start()
        setUp()
        ride(alice, "3kn", ago = 1.hours)
        ride(alice, "3ko", ago = 365.days, postedAgo = 364.days)
        ride(alice, "3kp", ago = 365.days)
        ride(
            alice,
            "3kq",
            ago = 365.days,
            extra = ""","combined":{"members":["${activityUri(alice, "3ko")}","${activityUri(alice, "3kp")}"],"sources":[]}""",
        )
        assertEquals(listOf("3kn", "3kq"), client.items(bob), "the combined ride stays a year back")
    }

    @Test
    fun profilesListByStart() = testApplication {
        start()
        setUp()
        ride(alice, "3ka", ago = 10.days, postedAgo = 9.days)
        ride(alice, "3kb", ago = 400.days)
        ride(alice, "3kc", ago = 5.days, postedAgo = 4.days)
        ride(alice, "3kd", ago = 401.days, postedAgo = 1.minutes)
        importHistory(10)
        val firstPage = client.query(GetAuthorActivities.method, bob, "?actor=$alice&limit=7")
        val secondPage = client.query(GetAuthorActivities.method, bob, "?actor=$alice&limit=7&cursor=${firstPage.cursor}")
        val all = (firstPage.activities + secondPage.activities).map { it.uri.rkey!! }
        val expected = listOf("3kc", "3ka") + (9 downTo 0).map { "3kh00$it" } + listOf("3kb", "3kd")
        assertEquals(expected, all, "newest started first, never collapsed, no gaps")
    }

    @Test
    fun aBigImportCollapsesIntoOneCard() = testApplication {
        start()
        setUp()
        ride(alice, "3ka", ago = 2.days, postedAgo = 2.days)
        importHistory(40)
        ride(alice, "3kz", ago = 1.days, importBatch = batch)
        assertEquals(listOf("3kz", "card:$batch", "3ka"), client.items(bob), "the recent one stays on its own")
        val card = client.card(bob)
        assertEquals(40, card.count)
        assertEquals(listOf("ride" to 40L), card.sports.map { it.sport to it.count })
        assertEquals((now - 365.days).toEpochMilliseconds(), card.earliestStartedAt.toEpochMilliseconds())
        assertEquals((now - 326.days).toEpochMilliseconds(), card.latestStartedAt.toEpochMilliseconds())
    }

    @Test
    fun aSmallImportStaysUncollapsed() = testApplication {
        start()
        setUp()
        importHistory(4)
        assertEquals((3 downTo 0).map { "3kh00$it" }, client.items(bob))
    }

    @Test
    fun theThresholdCountsWhatTheViewerMaySee() = testApplication {
        start()
        setUp()
        profile(carol)
        follow(carol, alice)
        follow(alice, carol)
        importHistory(3, prefix = "3kp", importBatch = batch)
        importHistory(3, prefix = "3km", importBatch = batch, tier = Visibility.MUTUALS)
        assertEquals(listOf("card:$batch"), client.items(carol), "a mutual sees 6")
        assertEquals(6, client.card(carol).count)
        assertEquals((2 downTo 0).map { "3kp00$it" }, client.items(bob), "a follower sees only the 3 public ones")
    }

    @Test
    fun privateActivitiesNeverReachTheCard() = testApplication {
        start()
        setUp()
        importHistory(30, prefix = "3kp")
        repeat(10) { i ->
            ride(
                alice,
                "3ks${i.toString().padStart(3, '0')}",
                ago = 200.days,
                tier = Visibility.SELF,
                importBatch = batch,
                sport = "run",
                extra = ""","distance":5000""",
            )
        }
        val card = client.card(bob)
        assertEquals(30, card.count)
        assertEquals(listOf("ride"), card.sports.map { it.sport })
        assertNull(card.distance, "only the private runs had distances")
        assertEquals(40, client.card(alice).count, "alice sees her own")
        assertEquals(50_000, client.card(alice).distance)
    }

    @Test
    fun aDistanceSharedNarrowerIsLeftOut() = testApplication {
        start()
        setUp()
        importHistory(4, prefix = "3kp")
        ride(alice, "3kd", ago = 200.days, postedAgo = 1.minutes, importBatch = batch, extra = ""","distance":7000""")
        // Its distance shared only with followers: the public record has none, its followers' details do.
        ride(alice, "3ke", ago = 201.days, postedAgo = 1.minutes, importBatch = batch)
        runBlocking {
            indexer.spaceWrite(
                SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, Visibility.FOLLOWERS),
                Nsid.FEED_ACTIVITY_DETAILS,
                "3ke",
                "bafy3ked",
                AtJson.parseToJsonElement(
                        """{"activity":"${activityUri(alice, "3ke")}","distance":9000,"createdAt":"2026-10-05T08:00:00Z"}""",
                    )
                    .jsonObject,
            )
        }
        assertEquals(6, client.card(bob).count)
        assertEquals(7000, client.card(bob).distance, "only the records' own distances count")
    }

    @Test
    fun cardsCarryRoutesFromTheLatestStarted() = testApplication {
        start()
        setUp()
        repeat(6) { i -> ride(alice, "3kr00$i", ago = 100.days - i.days, importBatch = batch, extra = ""","polyline":"route$i"""") }
        assertEquals(listOf("route5", "route4", "route3", "route2"), client.card(bob).routes?.map { it.polyline })
    }

    @Test
    fun filteredTimelinesDoNotCollapse() = testApplication {
        start()
        setUp()
        importHistory(6)
        assertEquals((5 downTo 0).map { "3kh00$it" }, client.items(bob, "&type=ride"))
    }

    @Test
    fun cardsPageWithoutGapsOrDuplicates() = testApplication {
        start()
        setUp()
        repeat(10) { i -> ride(alice, "3ka${i.toString().padStart(2, '0')}", ago = 3.days, postedAgo = (i + 1).hours) }
        importHistory(20)
        repeat(10) { i -> ride(alice, "3kb${i.toString().padStart(2, '0')}", ago = 300.days - i.days) }
        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = client.query(GetTimeline.method, bob, "?limit=4" + cursor?.let { "&cursor=$it" }.orEmpty())
            seen +=
                page.feed.map {
                    when (it) {
                        is TimelineItem.Activity -> it.view.uri.rkey!!
                        is TimelineItem.ImportCard -> "card"
                        is TimelineItem.Post -> "post:${it.post.uri.rkey}"
                        is TimelineItem.Repost -> "repost:${it.reposted.post.uri.rkey}"
                        is TimelineItem.Unknown -> "unknown"
                    }
                }
            cursor = page.cursor
        } while (cursor != null)
        val expected = listOf("card") + (0 until 10).map { "3ka${it.toString().padStart(2, '0')}" } + (9 downTo 0).map { "3kb0$it" }
        assertEquals(expected, seen)
    }

    @Test
    fun mutedAndBlockedAthletesHaveNoCard() = testApplication {
        start()
        setUp()
        profile(carol)
        follow(carol, alice)
        importHistory(6)
        interactionsWrite(bob, Nsid.GRAPH_MUTE, alice)
        interactionsWrite(alice, Nsid.GRAPH_BLOCK, carol)
        assertEquals(emptyList(), client.items(bob))
        assertEquals(emptyList(), client.items(carol))
    }

    @Test
    fun takenDownActivitiesAreNotCounted() = testApplication {
        start()
        setUp()
        importHistory(5)
        label(activityUri(alice, "3kh000"), "!takedown")
        assertEquals((4 downTo 1).map { "3kh00$it" }, client.items(bob), "4 left: no card")
    }

    @Test
    fun malformedBatchesAreIgnored() = testApplication {
        start()
        setUp()
        importHistory(6, importBatch = "not-a-tid")
        assertEquals((5 downTo 0).map { "3kh00$it" }, client.items(bob))
    }

    @Test
    fun twoBatchesGetTwoCards() = testApplication {
        start()
        setUp()
        importHistory(5, prefix = "3kh", importBatch = batch)
        importHistory(5, prefix = "3ki", importBatch = otherBatch)
        assertEquals(setOf("card:$batch", "card:$otherBatch"), client.items(bob).toSet())
    }

    @Test
    fun theBatchQueryListsTheWholeBatchByStart() = testApplication {
        start()
        setUp()
        importHistory(8)
        ride(alice, "3kz", ago = 1.days, importBatch = batch)
        ride(alice, "3ky", ago = 2.days, importBatch = batch, tier = Visibility.SELF)
        ride(alice, "3kx", ago = 3.days, importBatch = otherBatch)
        val first = client.query(GetImportBatch.method, bob, "?actor=$alice&batch=$batch&limit=5")
        val second = client.query(GetImportBatch.method, bob, "?actor=$alice&batch=$batch&limit=5&cursor=${first.cursor}")
        val expected = listOf("3kz") + (7 downTo 0).map { "3kh00$it" }
        assertEquals(expected, (first.activities + second.activities).map { it.uri.rkey!! }, "recent included, private left out")
        assertEquals(emptyList(), client.query(GetImportBatch.method, bob, "?actor=$alice&batch=3m5zzzzzzzzzz").activities)
        assertEquals(emptyList(), client.query(GetImportBatch.method, bob, "?actor=$alice&batch=nope").activities)
        val unknown = client.raw(GetImportBatch.method.nsid, bob, "?actor=did:plc:nobody&batch=$batch")
        assertTrue("ActorNotFound" in unknown.bodyAsText())
    }
}
