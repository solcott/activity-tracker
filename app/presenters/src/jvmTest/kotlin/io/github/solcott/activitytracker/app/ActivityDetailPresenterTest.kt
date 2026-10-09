package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.KudosList
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class ActivityDetailPresenterTest {
    private val alice = Did("did:plc:alice")
    private val activityUri = "at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob1"
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    private val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    private val staleAlice = """{"did":"did:plc:alice","handle":"alice.test"}"""
    private var kudosFromAppView = listOf(carol)
    private var streams: String? = null

    private val activity =
        """{"uri":"$activityUri","cid":"bafy3kbob1","author":$bob,""" +
            """"record":{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"},""" +
            """"kudosCount":${kudosFromAppView.size},"commentCount":0,"indexedAt":"2026-09-30T10:31:01Z","viewer":{}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        respond("""{"activity":$activity,"comments":[]}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        streams?.let { respond(it, headers = json) }
                            ?: respond("""{"error":"NotFound","message":"none"}""", HttpStatusCode.NotFound, json)
                    }

                    "/xrpc/${Nsid.FEED_GET_KUDOS}" -> {
                        respond("""{"uri":"$activityUri","kudos":[${kudosFromAppView.joinToString(",")}]}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond("""{"did":"did:plc:alice","handle":"alice.test","displayName":"Alice"}""", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private val localKudos = LocalKudos()

    private class Repos(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
    )

    private suspend fun repos(): Repos {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val outbox = OutboxProcessor(db, clients, Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                feed,
                localKudos,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Repos(
            feed,
            activities,
            ProfileRepository(clients, Clock.System),
            ActivityManager(db, clients, outbox, writer, streams, feed),
        )
    }

    private val screen = ActivityDetailScreen(activityUri)

    @Test
    fun showsHidesAndOpensKudosAthletes() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({ ActivityDetailPresenter(screen, navigator, r.feed, r.activities, r.profiles, localKudos, r.manager) }) {
            val loaded = awaitLoaded()
            assertNull(loaded.kudosList)

            loaded.eventSink(ActivityDetailEvent.ToggleKudosList)
            val shown = awaitKudos { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.kudosList?.athletes?.map { it.handle.value })

            shown.eventSink(ActivityDetailEvent.OpenProfile(Did("did:plc:carol")))
            assertEquals(ProfileScreen("did:plc:carol"), navigator.awaitNextScreen())

            shown.eventSink(ActivityDetailEvent.ToggleKudosList)
            var hidden = awaitItem() as ActivityDetailState.Loaded
            while (hidden.kudosList != null) hidden = awaitItem() as ActivityDetailState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersOwnToggleShowsBeforeTheAppViewCatchesUp() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos, r.manager)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleKudosList)
            awaitKudos { it.athletes.isNotEmpty() }

            // Alice gives kudos locally; the AppView still lists only Carol, so Alice is added from her profile.
            localKudos.set(AtUri(activityUri), AtUri("at://did:plc:alice/${Nsid.FEED_KUDOS}/3kk1"))
            val given = awaitKudos { it.athletes.size == 2 }
            assertEquals(listOf("Alice", null), given.kudosList?.athletes?.map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersRemovedKudosIsDroppedFromAStaleList() = runTest {
        kudosFromAppView = listOf(staleAlice, carol)
        val r = repos()
        localKudos.set(AtUri(activityUri), null)
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos, r.manager)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleKudosList)
            val shown = awaitKudos { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.kudosList?.athletes?.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chartsTheStreamsTheViewerMaySee() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null]},""" +
                """"heartRateZones":{"model":"cogganHeartRate","lows":[0,138,154,164,176]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos, r.manager)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            val charts = state.charts!!
            assertEquals(listOf(StreamKind.HeartRate), charts.lines.map { it.kind })
            assertEquals(listOf(1, 1, 1, 0, 0), charts.heartRateZones?.map { it.seconds })
            assertFalse(state.chartsMissingZones)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sectionsStartCollapsedAndToggleOneOrAll() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate","power"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null],"power":[200,250,300,null]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos, r.manager)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            assertEquals(listOf(DetailSection.HeartRate, DetailSection.Power), state.sections)
            assertEquals(emptySet(), state.expanded)

            state.eventSink(ActivityDetailEvent.ToggleSection(DetailSection.HeartRate))
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(setOf(DetailSection.HeartRate), state.expanded)

            // Something open, so all collapse; then nothing open, so all expand.
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == false } as ActivityDetailState.Loaded
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(state.sections.toSet(), state.expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStreamsMeansNoCharts() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos, r.manager)
        }) {
            assertNull(awaitLoaded().charts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitKudos(done: (KudosList) -> Boolean): ActivityDetailState.Loaded {
        var state = awaitLoaded()
        while (state.kudosList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }
}
