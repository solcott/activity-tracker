package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.feed.FeedEvent
import io.github.solcott.activitytracker.app.feed.FeedPresenter
import io.github.solcott.activitytracker.app.feed.FeedScope
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

class FeedPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }

    private class Repos(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val segmentPrefs: SegmentPrefsRepository,
    )

    private suspend fun repos(): Repos {
        val (db, _, clients) = signedInTestSession(http)
        val localLikes = LocalLikes()
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                ActivityPlaces(clients, Clock.System, Logger),
                privacy,
                RouteTrimmer(db, clients, zones.writer, zones.outbox, privacy, zones.tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).sweeper,
            )
        return Repos(feed, activities, ProfileRepository(clients, Clock.System, Logger, zones.store), segmentsOf(clients).prefs)
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator,
        tracker: LocationTracker,
        block: suspend ReceiveTurbine<FeedState>.() -> Unit,
    ) {
        val r = repos()
        presenterTestOf(
            { FeedPresenter(navigator, r.feed, r.activities, r.profiles, tracker, Clock.System, r.segmentPrefs) },
            timeout = TURBINE_TIMEOUT,
            block = block,
        )
    }

    /** Feed queries the AppView received: the method and its params. */
    private val queries: MutableList<Pair<String, Map<String, List<String>>>> = Collections.synchronizedList(mutableListOf())

    /** An AppView that records feed queries and answers a `type`-filtered one with a ride. */
    private val appView =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath
                when {
                    path == "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    path == "/xrpc/com.atproto.space.getRecord" && request.url.parameters["collection"] == Nsid.ACTOR_PRIVACY_ZONES -> {
                        zonesReads.incrementAndGet()
                        zonesGate?.await()
                        respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                    }
                    path == "/xrpc/${Nsid.FEED_GET_TIMELINE}" || path == "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" -> {
                        val params = request.url.parameters.entries().associate { it.key to it.value }
                        queries += path.removePrefix("/xrpc/") to params
                        val activities = if ("type" in params) listOf(ride) else timeline
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output(activities)), headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    /** Holds the privacy zones read (the route re-trim) until completed; null answers at once. */
    private var zonesGate: CompletableDeferred<Unit>? = null
    private val zonesReads = AtomicInteger()

    /** What an unfiltered timeline query answers. */
    private var timeline = emptyList<ActivityView>()

    private val bobsFollowersRun =
        ActivityView(
            uri = AtUri.of(Did("did:plc:bob"), Nsid.FEED_ACTIVITY, "3kbob"),
            cid = Cid("bafy3kbob"),
            author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
            record = ActivityRecord("run", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
            visibility = Visibility.FOLLOWERS,
            viewer = ActivityViewerState(),
        )

    private val ride =
        ActivityView(
            uri = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3kride"),
            cid = Cid("bafy3kride"),
            author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    private suspend fun filterTest(
        scope: String = OAuthClientConfig.DEFAULT_SCOPE,
        block: suspend ReceiveTurbine<FeedState>.() -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(appView, scope = scope)
        val localLikes = LocalLikes()
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                ActivityPlaces(clients, Clock.System, Logger),
                privacy,
                RouteTrimmer(db, clients, zones.writer, zones.outbox, privacy, zones.tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).sweeper,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
        presenterTestOf(
            {
                FeedPresenter(
                    FakeNavigator(FeedScreen),
                    feed,
                    activities,
                    profiles,
                    UnsupportedLocationTracker(),
                    Clock.System,
                    segmentsOf(clients).prefs,
                )
            },
            timeout = TURBINE_TIMEOUT,
            block = block,
        )
    }

    /** Waits out the refresh in flight and returns the settled state. */
    private suspend fun ReceiveTurbine<FeedState>.awaitRefreshed(): FeedState {
        awaitUntil { it.refreshing }
        return awaitUntil { !it.refreshing }
    }

    @Test
    fun likesAFollowersActivity() = runTest {
        timeline = listOf(bobsFollowersRun)
        filterTest {
            val state = awaitUntil { it.activities.isNotEmpty() }
            state.eventSink(FeedEvent.Activity(ActivityListEvent.ToggleLike(state.activities.single())))
            val liked = awaitUntil { it.activities.singleOrNull()?.viewer?.like != null }
            assertEquals(1, liked.activities.single().likeCount)
            assertEquals(Visibility.FOLLOWERS, liked.activities.single().visibility)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksLikesIsAskedToSignInAgain() = runTest {
        timeline = listOf(bobsFollowersRun)
        // Signed in before the interactions space's declaration listed likes.
        filterTest(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}") {
            val state = awaitUntil { it.activities.isNotEmpty() && !it.refreshing }
            assertEquals(null, state.error)
            state.eventSink(FeedEvent.Activity(ActivityListEvent.ToggleLike(state.activities.single())))
            val refused = awaitUntil { it.error != null }
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.error)
            assertEquals(null, refused.activities.single().viewer?.like, "the like isn't shown")
            assertEquals(0, refused.activities.single().likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun opensOnTheUnfilteredFollowingFeed() = runTest {
        filterTest {
            val state = awaitRefreshed()
            assertEquals(FeedScope.Following, state.scope)
            assertEquals(FeedFilter.Default, state.filter)
            assertEquals(FeedFilterScreen(FeedFilter.Default), state.filterScreen)
            val (method, params) = queries.single()
            assertEquals(Nsid.FEED_GET_TIMELINE, method)
            assertEquals(null, params["type"])
            assertEquals(null, params["since"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theFeedLoadsWhileTheRouteReTrimIsStillRunning() = runTest {
        zonesGate = CompletableDeferred()
        filterTest {
            awaitRefreshed()
            assertEquals(Nsid.FEED_GET_TIMELINE, queries.single().first)
            assertTrue(zonesReads.get() > 0, "the re-trim started and is waiting on the zones")
            cancelAndIgnoreRemainingEvents()
        }
        zonesGate?.complete(Unit)
    }

    @Test
    fun mineReadsTheViewersOwnActivities() = runTest {
        filterTest {
            val opened = awaitRefreshed()
            opened.eventSink(FeedEvent.SetScope(FeedScope.Mine))
            val state = awaitRefreshed()
            assertEquals(FeedScope.Mine, state.scope)
            val (method, params) = queries.last()
            assertEquals(Nsid.FEED_GET_AUTHOR_ACTIVITIES, method)
            assertEquals(listOf("did:plc:alice"), params["actor"])
            assertEquals(opened.filterScreen, state.filterScreen, "the filter control isn't rebuilt per change")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearingFromTheEmptyFeedReseedsTheControlAndKeepsTheScope() = runTest {
        filterTest {
            awaitRefreshed().eventSink(FeedEvent.SetScope(FeedScope.Mine))
            val narrowed = FeedFilter(persistentSetOf(ActivityType("hike")))
            awaitUntil { it.scope == FeedScope.Mine }.eventSink(FeedEvent.FilterChanged(narrowed))
            awaitUntil { it.filter == narrowed }.eventSink(FeedEvent.ClearFilters)
            val cleared = awaitUntil { it.filter == FeedFilter.Default }
            assertEquals(FeedScope.Mine, cleared.scope)
            assertEquals(FeedFilterScreen(FeedFilter.Default), cleared.filterScreen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun typeAndDateFiltersGoToTheAppViewAndShowTheirOwnCache() = runTest {
        filterTest {
            val opened = awaitRefreshed()
            assertEquals(emptyList(), opened.activities)
            val filter =
                FeedFilter(
                    types = persistentSetOf(ActivityType("ride")),
                    range = DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)),
                )
            opened.eventSink(FeedEvent.FilterChanged(filter))
            val state = awaitUntil { it.activities.isNotEmpty() }
            assertEquals(listOf(ride.uri), state.activities.map { it.uri })
            val zone = TimeZones.device()
            val params = queries.last().second
            assertEquals(listOf("ride"), params["type"])
            assertEquals(listOf(LocalDate(2026, 3, 1).atStartOfDayIn(zone).toString()), params["since"])
            assertEquals(listOf(LocalDate(2026, 4, 1).atStartOfDayIn(zone).toString()), params["until"], "the last day is included")
            assertEquals(1, queries.size - 1, "a filter change refreshes once")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun phonesOfferRecording() = runTest {
        val navigator = FakeNavigator(FeedScreen)
        presenterTest(navigator, FakeLocationTracker()) {
            val state = awaitItem()
            assertTrue(state.canRecord)
            state.eventSink(FeedEvent.Record)
            assertEquals(RecordScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun desktopAndWebOnlyOfferManualEntry() = runTest {
        presenterTest(FakeNavigator(FeedScreen), UnsupportedLocationTracker()) {
            val state = awaitItem()
            assertFalse(state.canRecord)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * How long a test waits for the next state. Turbine's default of 1 s is too short for a refresh (Room and the mock AppView) while
 * `./gradlew check` runs other test tasks alongside, and a stuck test still fails within this.
 */
private val TURBINE_TIMEOUT = 10.seconds
