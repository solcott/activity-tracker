package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.MatchSpan
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.ownActivitiesAround
import io.github.solcott.activitytracker.data.feed.windows
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class OwnActivitySearchTest {
    private val did = Did("did:plc:alice")
    private val queried = mutableListOf<Pair<Instant, Instant>>()

    /** The AppView's index of the athlete's log; [pageSize] activities a page, the cursor an offset. */
    private var log = listOf<ActivityView>()
    private var pageSize = 100
    private var failFrom: Instant? = null

    private fun view(rkey: String, start: Instant) =
        ActivityView(
            uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = record(start),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> Unit
                    else -> return@MockEngine respondError(HttpStatusCode.NotFound)
                }
                val since = Instant.parse(request.url.parameters["since"]!!)
                val until = Instant.parse(request.url.parameters["until"]!!)
                if (failFrom?.let { since >= it } == true) return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
                val offset = request.url.parameters["cursor"]?.toInt() ?: 0
                if (offset == 0) queried += since to until
                val inRange = log.filter { it.record.startedAt >= since && it.record.startedAt < until }
                val page = inRange.drop(offset).take(pageSize)
                val next = (offset + pageSize).takeIf { it < inRange.size }?.toString()
                val output = GetAuthorActivitiesByStart.Output(page, next)
                respond(AtJson.encodeToString(GetAuthorActivitiesByStart.Output.serializer(), output), headers = json)
            },
        )

    private fun record(start: Instant) = ActivityRecord(Sport.RUN, start, 3600, Instant.parse("2026-10-01T00:00:00Z"))

    private fun cached(view: ActivityView) =
        FeedItemEntity("timeline", view.uri.value, 0, AtJson.encodeToString(ActivityView.serializer(), view))

    private suspend fun session(): Pair<AppDatabase, SessionClients> {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return db to clients
    }

    private suspend fun logUpload(db: AppDatabase, rkey: String, start: Instant) {
        val json = AtJson.encodeToString(ActivityRecord.serializer(), record(start))
        db.uploads()
            .insert(
                UploadItemEntity(
                    did = did.value,
                    rkey = rkey,
                    batchId = null,
                    kind = UploadItemEntity.KIND_NEW,
                    title = null,
                    sport = Sport.RUN,
                    subSport = null,
                    startedAt = start.toEpochMilliseconds(),
                    queuedAt = 0,
                    recordJson = json,
                ),
            )
    }

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, LocalLikes(), Logger)
    }

    private fun span(start: Instant) = MatchSpan(start, 1.hours, zoneless = false)

    @Test
    fun findsALoggedActivityInEachYearOfALongBatch() = runTest {
        val starts = listOf("2021-03-01T07:00:00Z", "2022-07-15T07:00:00Z", "2023-11-30T07:00:00Z").map(Instant::parse)
        log = starts.mapIndexed { i, start -> view("3k$i", start + 1.minutes) } + view("3kx", Instant.parse("2022-01-01T07:00:00Z"))

        val search = feed().ownActivitiesAround(starts.map(::span))

        assertTrue(search.complete)
        assertEquals(listOf("3k0", "3k1", "3k2"), search.activities.map { it.uri.rkey }, "only near the batch's activities")
        assertEquals(3, queried.size, "one window per activity, years apart")
    }

    @Test
    fun halvesAWindowWithMorePagesThanItReads() = runTest {
        val first = Instant.parse("2024-01-01T07:00:00Z")
        val days = (0 until 20).map { first + it.days }
        log = days.mapIndexed { i, start -> view("3k$i", start) }
        pageSize = 2

        // At most two pages a read: four activities, while the merged window holds twenty.
        val search = feed().ownActivitiesAround(days.map(::span), maxPages = 2)

        assertTrue(search.complete)
        assertEquals(20, search.activities.size)
        assertTrue(queried.size > 1)
    }

    @Test
    fun aFailedWindowSearchesTheRestOnTheDevice() = runTest {
        val early = Instant.parse("2020-05-01T07:00:00Z")
        val late = Instant.parse("2024-05-01T07:00:00Z")
        val later = Instant.parse("2025-05-01T07:00:00Z")
        log = listOf(view("3ka", early), view("3kb", late), view("3kc", later))
        failFrom = Instant.parse("2022-01-01T00:00:00Z")
        val (db, clients) = session()
        // The cached feed holds one of the athlete's activities; the upload log, one saved here.
        db.feed().upsert(listOf(cached(view("3kb", late))))
        logUpload(db, "3kd", later + 5.minutes)

        val search = FeedRepository(db, clients, LocalLikes(), Logger).ownActivitiesAround(listOf(span(early), span(late), span(later)))

        assertFalse(search.complete)
        assertTrue(search.localOnly)
        assertEquals(listOf("3ka", "3kb", "3kd"), search.activities.map { it.uri.rkey }, "the AppView's, then the device's")
        assertEquals(null, search.activities.last().cid, "a logged activity counts as still uploading")
        assertEquals(1, queried.size, "the AppView isn't asked again once it failed")
    }

    @Test
    fun theOutboxCountsWithTheAppViewDown() = runTest {
        val start = Instant.parse("2024-05-01T07:00:00Z")
        failFrom = Instant.DISTANT_PAST
        val (db, clients) = session()
        OutboxWriter(db, clients, Clock.System).put(Nsid.FEED_ACTIVITY, "3ke", ActivityRecord.serializer(), record(start))
        OutboxWriter(db, clients, Clock.System).put(Nsid.FEED_ACTIVITY, "3kf", ActivityRecord.serializer(), record(start + 30.days))

        val search = FeedRepository(db, clients, LocalLikes(), Logger).ownActivitiesAround(listOf(span(start)))

        assertFalse(search.complete)
        assertTrue(search.localOnly)
        assertEquals(listOf("3ke"), search.activities.map { it.uri.rkey }, "only in the batch's window")
    }

    @Test
    fun anActivityTheAppViewAndTheLogBothHoldIsListedOnce() = runTest {
        val start = Instant.parse("2024-05-01T07:00:00Z")
        log = listOf(view("3ka", start))
        val (db, clients) = session()
        logUpload(db, "3ka", start)
        db.feed().upsert(listOf(cached(view("3ka", start))))

        val search = FeedRepository(db, clients, LocalLikes(), Logger).ownActivitiesAround(listOf(span(start)))

        assertTrue(search.complete)
        assertFalse(search.localOnly)
        assertEquals(listOf(Cid("bafy3ka")), search.activities.map { it.cid }, "once, as the AppView serves it")
    }

    @Test
    fun othersCachedActivitiesAreLeftOut() = runTest {
        val start = Instant.parse("2024-05-01T07:00:00Z")
        failFrom = Instant.DISTANT_PAST
        val (db, clients) = session()
        val bobDid = Did("did:plc:bob")
        val bob =
            view("3kg", start)
                .copy(uri = AtUri.of(bobDid, Nsid.FEED_ACTIVITY, "3kg"), author = ProfileViewBasic(bobDid, Handle("bob.test")))
        db.feed().upsert(listOf(cached(bob)))

        val search = FeedRepository(db, clients, LocalLikes(), Logger).ownActivitiesAround(listOf(span(start)))

        assertEquals(emptyList(), search.activities)
    }

    @Test
    fun windowsMergeOverlappingSpansUpToTheLimit() {
        val first = Instant.parse("2024-01-01T07:00:00Z")
        val daily = (0 until 70).map { span(first + it.days) }
        val windows = windows(daily, 14.hours, 31.days)
        assertEquals(3, windows.size)
        assertTrue(windows.all { it.endExclusive - it.start <= 31.days })
        assertEquals(first - 14.hours, windows.first().start)
    }
}
