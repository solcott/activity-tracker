package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.feed.FeedEvent
import io.github.solcott.activitytracker.app.feed.FeedPresenter
import io.github.solcott.activitytracker.app.feed.FeedScope
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
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
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

class FeedPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }

    private class Repos(val feed: FeedRepository, val activities: ActivityRepository, val profiles: ProfileRepository)

    private suspend fun repos(): Repos {
        val (db, _, clients) = signedInTestSession(http)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                OutboxProcessor(db, clients, Logger),
                OutboxWriter(db, clients, Clock.System),
                PdsActivityStreamsStore(OutboxWriter(db, clients, Clock.System), clients, Clock.System),
                feed,
                localKudos,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Repos(feed, activities, ProfileRepository(clients, Clock.System, Logger))
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator,
        tracker: LocationTracker,
        block: suspend ReceiveTurbine<FeedState>.() -> Unit,
    ) {
        val r = repos()
        presenterTestOf({ FeedPresenter(navigator, r.feed, r.activities, r.profiles, tracker, Clock.System) }, block = block)
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
                    path == "/xrpc/${Nsid.FEED_GET_TIMELINE}" || path == "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" -> {
                        val params = request.url.parameters.entries().associate { it.key to it.value }
                        queries += path.removePrefix("/xrpc/") to params
                        val activities = if ("type" in params) listOf(ride) else emptyList()
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output(activities)), headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private val ride =
        ActivityView(
            uri = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3kride"),
            cid = Cid("bafy3kride"),
            author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            kudosCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    private suspend fun filterTest(block: suspend ReceiveTurbine<FeedState>.() -> Unit) {
        val (db, _, clients) = signedInTestSession(appView)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                OutboxProcessor(db, clients, Logger),
                OutboxWriter(db, clients, Clock.System),
                PdsActivityStreamsStore(OutboxWriter(db, clients, Clock.System), clients, Clock.System),
                feed,
                localKudos,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger)
        presenterTestOf(
            { FeedPresenter(FakeNavigator(FeedScreen), feed, activities, profiles, UnsupportedLocationTracker(), Clock.System) },
            block = block,
        )
    }

    /** Waits out the refresh in flight and returns the settled state. */
    private suspend fun ReceiveTurbine<FeedState>.awaitRefreshed(): FeedState {
        awaitUntil { it.refreshing }
        return awaitUntil { !it.refreshing }
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
