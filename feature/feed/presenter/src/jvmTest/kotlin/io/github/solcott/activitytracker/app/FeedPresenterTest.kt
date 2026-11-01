package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.feed.FeedEvent
import io.github.solcott.activitytracker.app.feed.FeedPresenter
import io.github.solcott.activitytracker.app.feed.FeedScope
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.feed.ImportBatchScreen
import io.github.solcott.activitytracker.app.feed.UnsentChangesScreen
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxPacer
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RejectedChanges
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
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
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SportCount
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

class FeedPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }

    @Suppress("LongParameterList") // What each test reaches for.
    private class Repos(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val segmentPrefs: SegmentPrefsRepository,
        val rejected: RejectedChanges,
        val db: AppDatabase,
        val clients: SessionClients,
    ) {
        /** The session's outbox loop, as `App` runs it. */
        val pacer = OutboxPacer(db, clients, activities, Clock.System, Logger)
    }

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
        return Repos(
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger, zones.store),
            segmentsOf(clients).prefs,
            RejectedChanges(db, clients, zones.outbox, feed, localLikes),
            db,
            clients,
        )
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator,
        tracker: LocationTracker,
        seed: suspend Repos.() -> Unit = {},
        block: suspend ReceiveTurbine<FeedState>.() -> Unit,
    ) {
        val r = repos().apply { seed() }
        presenterTestOf(
            { FeedPresenter(navigator, r.feed, r.activities, r.profiles, tracker, Clock.System, r.segmentPrefs, r.rejected, r.pacer) },
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
                    path == "/xrpc/com.atproto.server.describeServer" -> {
                        val flag = spacesAlpha?.let { ""","spacesAlpha":$it""" }.orEmpty()
                        respond("""{"did":"did:web:pds.test","availableUserDomains":[".test"]$flag}""", headers = json)
                    }
                    path == "/xrpc/com.atproto.space.getRecord" && request.url.parameters["collection"] == Nsid.ACTOR_PRIVACY_ZONES -> {
                        zonesReads.incrementAndGet()
                        zonesRead.complete(Unit)
                        zonesGate?.await()
                        respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                    }
                    path == "/xrpc/${Nsid.FEED_GET_TIMELINE}" || path == "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" -> {
                        val params = request.url.parameters.entries().associate { it.key to it.value }
                        queries += path.removePrefix("/xrpc/") to params
                        feedGate?.await()
                        if (feedFails) return@MockEngine respond("", HttpStatusCode.InternalServerError)
                        val activities = if ("type" in params) listOf(ride) else timeline
                        respond(
                            AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output.ofActivities(activities)),
                            headers = json,
                        )
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

    /** Completed once the privacy zones are first read. */
    private val zonesRead = CompletableDeferred<Unit>()

    /** Holds feed queries until completed, so the test sees one in flight; null answers at once. */
    private var feedGate: CompletableDeferred<Unit>? = null

    /** Whether feed queries fail with a server error. */
    private var feedFails = false

    /** What an unfiltered timeline query answers. */
    private var timeline = emptyList<ActivityView>()

    /** The `spacesAlpha` flag the PDS's `describeServer` answers with, which each session reads at its start; left out while null. */
    private var spacesAlpha: Boolean? = true

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
        clients.resolveSpacesSupport()
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
                    RejectedChanges(db, clients, zones.outbox, feed, localLikes),
                    OutboxPacer(db, clients, activities, Clock.System, Logger),
                )
            },
            timeout = TURBINE_TIMEOUT,
            block = block,
        )
    }

    /** Waits out the refresh in flight and returns the settled state. */
    private suspend fun ReceiveTurbine<FeedState>.awaitRefreshed(): FeedState {
        awaitUntil { it.content.isLoading }
        return awaitUntil { !it.content.isLoading }
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
            val state = awaitUntil { it.activities.isNotEmpty() && !it.content.isLoading }
            assertEquals(null, state.error)
            state.eventSink(FeedEvent.Activity(ActivityListEvent.ToggleLike(state.activities.single())))
            val refused = awaitUntil { it.error != null }
            assertEquals(UiError(UiError.Kind.SignInAgain), refused.error)
            assertEquals(null, refused.activities.single().viewer?.like, "the like isn't shown")
            assertEquals(0, refused.activities.single().likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPdsWithoutSpacesRefusesALikeOnAFollowersActivityWithItsOwnMessage() = runTest {
        timeline = listOf(bobsFollowersRun)
        spacesAlpha = null
        filterTest {
            val state = awaitUntil { it.activities.isNotEmpty() && !it.content.isLoading }
            state.eventSink(FeedEvent.Activity(ActivityListEvent.ToggleLike(state.activities.single())))
            val refused = awaitUntil { it.error != null }
            assertEquals(UiError(UiError.Kind.SpacesUnsupported), refused.error, "not a request to sign in again")
            assertEquals(null, refused.activities.single().viewer?.like, "the like isn't shown")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRefreshKeepsTheActivitiesListedWhileItRuns() = runTest {
        timeline = listOf(bobsFollowersRun)
        filterTest {
            val loaded = awaitRefreshed()
            val refresh = CompletableDeferred<Unit>().also { feedGate = it }
            loaded.eventSink(FeedEvent.Refresh)
            val refreshing = awaitUntil { it.content.isLoading }
            assertEquals(listOf(bobsFollowersRun.uri), refreshing.activities.map { it.uri })
            refresh.complete(Unit)
            val settled = awaitUntil { !it.content.isLoading }
            assertEquals(listOf(bobsFollowersRun.uri), settled.activities.map { it.uri })
            assertEquals(null, settled.content.errorOrNull)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRefreshKeepsTheActivitiesAndSaysWhy() = runTest {
        timeline = listOf(bobsFollowersRun)
        filterTest {
            val loaded = awaitRefreshed()
            feedFails = true
            loaded.eventSink(FeedEvent.Refresh)
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Http(500), failed.content.errorOrNull)
            assertEquals(listOf(bobsFollowersRun.uri), failed.activities.map { it.uri })
            assertFalse(failed.content.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFirstLoadFailureCanBeRetried() = runTest {
        feedFails = true
        filterTest {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertFalse(failed.content.hasLoaded, "nothing was cached, so there's nothing to show")
            feedFails = false
            timeline = listOf(bobsFollowersRun)
            failed.eventSink(FeedEvent.Refresh)
            val loaded = awaitUntil { it.activities.isNotEmpty() && !it.content.isLoading }
            assertEquals(null, loaded.content.errorOrNull)
            assertEquals(listOf(bobsFollowersRun.uri), loaded.activities.map { it.uri })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewFilterNeverShowsThePreviousFiltersActivities() = runTest {
        timeline = listOf(bobsFollowersRun)
        filterTest {
            val opened = awaitRefreshed()
            assertEquals(listOf(bobsFollowersRun.uri), opened.activities.map { it.uri })
            val rides = FeedFilter(types = persistentSetOf(ActivityType("ride")))
            opened.eventSink(FeedEvent.FilterChanged(rides))
            do {
                val state = awaitItem()
                val underRides = state.filter == rides
                if (underRides) assertFalse(bobsFollowersRun.uri in state.activities.map { it.uri }, "the run isn't listed under Rides")
            } while (!underRides || state.activities.isEmpty())
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
            // The feed may settle before the re-trim even reaches the zones; either way the re-trim is still held when it does.
            zonesRead.await()
            assertTrue(zonesGate?.isCompleted == false, "the re-trim is waiting on the zones")
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
    fun anImportCardOpensItsBatch() = runTest {
        val navigator = FakeNavigator(FeedScreen)
        val card =
            ImportCardView(
                author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test"), displayName = "Bob"),
                batch = "3m5abcdefghij",
                count = 40,
                earliestStartedAt = Instant.parse("2019-03-02T08:00:00Z"),
                latestStartedAt = Instant.parse("2026-01-01T08:00:00Z"),
                sports = listOf(SportCount("ride", 40)),
                postedAt = Instant.parse("2026-10-30T12:00:00Z"),
            )
        presenterTest(navigator, UnsupportedLocationTracker()) {
            awaitItem().eventSink(FeedEvent.OpenImportBatch(card))
            assertEquals(ImportBatchScreen("did:plc:bob", "3m5abcdefghij", "Bob"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aServerPauseShowsUntilWhenWhileChangesWait() = runTest {
        val until = Clock.System.now() + 2.hours
        var queued = 0L
        val seed: suspend Repos.() -> Unit = {
            clients.pacing.pause(until)
            queued =
                db.outbox()
                    .insert(
                        OutboxEntity(
                            did = "did:plc:alice",
                            operation = "put",
                            collection = Nsid.FEED_LIKE,
                            rkey = "3klike",
                            recordJson = "{}",
                            createdAt = 0,
                        )
                    )
        }
        var repos: Repos? = null
        presenterTest(
            FakeNavigator(FeedScreen),
            FakeLocationTracker(),
            {
                seed()
                repos = this
            },
        ) {
            val paused = awaitUntil { it.pendingWrites == 1 && it.uploadPausedUntil != null }
            assertEquals(until, paused.uploadPausedUntil)
            repos!!.db.outbox().delete(queued)
            awaitUntil { it.pendingWrites == 0 && it.uploadPausedUntil == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refusedChangesAreCountedApartAndOpenTheirList() = runTest {
        val navigator = FakeNavigator(FeedScreen)
        val seed: suspend Repos.() -> Unit = {
            val outbox = db.outbox()
            val did = "did:plc:alice"
            fun row(collection: String, rkey: String) =
                OutboxEntity(did = did, operation = "put", collection = collection, rkey = rkey, recordJson = "{}", createdAt = 0)
            outbox.markRejected(outbox.insert(row(Nsid.FEED_ACTIVITY, "3kact")), at = 1, error = "refused")
            outbox.insert(row(Nsid.FEED_ACTIVITY_DETAILS, "3kact"))
            outbox.markRejected(outbox.insert(row(Nsid.FEED_LIKE, "3klike")), at = 1, error = "refused")
        }
        presenterTest(navigator, FakeLocationTracker(), seed) {
            val state = awaitUntil { it.rejectedChanges == 2 }
            assertEquals(0, state.pendingWrites, "refused changes and what they hold aren't uploading")
            state.eventSink(FeedEvent.UnsentChanges)
            assertEquals(UnsentChangesScreen, navigator.awaitNextScreen())
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
