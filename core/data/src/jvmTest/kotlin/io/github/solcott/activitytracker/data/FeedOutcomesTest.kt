package io.github.solcott.activitytracker.data

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import co.touchlab.kermit.Logger
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
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
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
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class FeedOutcomesTest {
    private val did = Did("did:plc:alice")

    /** What the AppView's timeline lists, or null to fail it. */
    private var served: List<String>? = listOf("3kride")

    /** Holds the timeline's answer until completed, when set. */
    private var gate: CompletableDeferred<Unit>? = null

    private fun view(rkey: String) =
        ActivityView(
            uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" -> {
                        gate?.await()
                        val rkeys = served ?: return@MockEngine respondError(HttpStatusCode.InternalServerError)
                        val output = GetTimeline.Output(rkeys.map(::view))
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), output), headers = json)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        )

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, LocalLikes(), Logger)
    }

    private val Outcome<List<ActivityView>>.rkeys
        get() = (this as? Outcome.Data)?.data?.map { it.uri.rkey }

    /** Skips outcomes until one matches [predicate]; Room may read the same page more than once. */
    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun cachedPageShowsThenTheRefreshedOne() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        served = listOf("3kride", "3krun")
        val answer = CompletableDeferred<Unit>().also { gate = it }
        feed.timelineOutcomes().test {
            assertEquals(Outcome.Loading, awaitItem())
            val cached = awaitItem()
            answer.complete(Unit)
            assertEquals(Origin.Cache, assertIs<Outcome.Data<*>>(cached).origin)
            assertEquals(listOf("3kride"), cached.rkeys)
            awaitUntil { it is Outcome.Data && it.origin == Origin.Network && it.rkeys == listOf("3kride", "3krun") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyCacheIsHeldBackUntilTheRefreshAnswers() = runTest {
        feed().timelineOutcomes().test {
            assertEquals(Outcome.Loading, awaitItem())
            // Room's read of the refreshed page may beat the refresh's own answer, so either may come first, but never the empty miss.
            assertEquals(listOf("3kride"), awaitItem().rkeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyAnswerIsShownOnceTheRefreshAnswers() = runTest {
        served = emptyList()
        feed().timelineOutcomes().test {
            assertEquals(Outcome.Loading, awaitItem())
            assertEquals(Outcome.Data(emptyList<ActivityView>(), Origin.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRefreshKeepsTheCacheAndEndsOnTheFailure() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        served = null
        feed.timelineOutcomes().test {
            assertEquals(Outcome.Loading, awaitItem())
            val seen = mutableListOf<Outcome<List<ActivityView>>>()
            // A cached read after the failure is followed by the failure again, so it always ends on one.
            while (seen.none { it is Outcome.Data } || seen.lastOrNull() !is Outcome.Error) seen += awaitItem()
            assertEquals(listOf("3kride"), seen.first { it is Outcome.Data }.rkeys)
            assertEquals(Outcome.Error(DataError.Http(500), Origin.Network), seen.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyCacheIsNeverAnAnswerWhenTheRefreshFails() = runTest {
        served = null
        feed().timelineOutcomes().test {
            assertEquals(Outcome.Loading, awaitItem())
            assertEquals(Outcome.Error(DataError.Http(500), Origin.Network), awaitItem())
            // Room's first read, empty, may come after the failure: it's still a miss.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failureBeforeRefreshFailsIt() = runTest {
        feed().timelineOutcomes(beforeRefresh = { throw kotlinx.io.IOException("offline") }).test {
            assertEquals(Outcome.Loading, awaitItem())
            assertEquals(Outcome.Error(DataError.Network, Origin.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
