package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.ActivityDetailRouteTest.Companion.SERVED_POLYLINE
import io.github.solcott.activitytracker.app.ActivityDetailRouteTest.Companion.SERVED_ROUTE
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.map.RouteMapEvent
import io.github.solcott.activitytracker.app.map.RouteMapPresenter
import io.github.solcott.activitytracker.app.map.RouteMapScreen
import io.github.solcott.activitytracker.app.map.RouteMapState
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

/** The enlarged route map (`feed/route-map-viewer`): opened from the detail page, drawing what its map draws, and left with back. */
class RouteMapPresenterTest : ActivityDetailTestBase() {
    private val start = Instant.parse("2026-09-30T10:00:00Z")
    private val route =
        (0..100).map { LocationSample(51.5, -0.12 + it * 0.0001, null, speed = null, accuracy = 5.0, time = start + it.seconds) }
    private val mapScreen = RouteMapScreen(activityUri)

    private suspend fun map(
        r: Repos,
        navigator: FakeNavigator = FakeNavigator(mapScreen),
        block: suspend CircuitReceiveTurbine<RouteMapState>.() -> Unit,
    ) = presenterTestOf({ RouteMapPresenter(mapScreen, navigator, r.feed, r.activities, r.profiles) }, block = block)

    private suspend fun ReceiveTurbine<RouteMapState>.awaitRoute() = awaitUntil { it.route != null }

    private val RouteMapState.route
        get() = content.data?.route

    @Test
    fun theDetailMapOpensTheEnlargedMap() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                navigator,
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.OpenMap)
            assertEquals(RouteMapScreen(activityUri), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aViewerSeesTheServedPolylineMarkedAtItsOwnEnds() = runTest {
        track = route
        recordExtra = """"title":"Morning run",$SERVED_ROUTE"""
        map(repos()) {
            val state = awaitRoute()
            assertEquals(ShownRoute(SERVED_POLYLINE, persistentListOf(1), ends = null), state.route)
            assertEquals("Morning run", state.content.data?.title)
            assertEquals("run", state.content.data?.activityType?.sport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theOwnerSeesTheirFullTrackMarkedWhereOthersRoutesStartAndEnd() = runTest {
        author = staleAlice
        track = route
        recordExtra = SERVED_ROUTE
        map(repos()) {
            val state = awaitUntil { it.route?.ends?.start != null }
            assertEquals(ActivityDraft.polylineOf(route)?.polyline, state.route?.polyline)
            assertEquals(
                RouteEnds(route.first().let { it.latitude to it.longitude }, route.last().let { it.latitude to it.longitude }),
                state.route?.ends,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryLoadsIt() = runTest {
        offline = true
        recordExtra = SERVED_ROUTE
        map(repos()) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertFalse(failed.content.hasLoaded)
            assertNull(failed.route)

            offline = false
            failed.eventSink(RouteMapEvent.Retry)
            assertEquals(SERVED_POLYLINE, awaitRoute().route?.polyline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theAthletesOwnRewriteChangesTheShownRoute() = runTest {
        recordExtra = SERVED_ROUTE
        val r = repos()
        map(r) {
            awaitRoute()
            val uri = AtUri(activityUri)
            r.feed.rewrites.set(uri, r.feed.activity(uri).record.copy(polyline = REWRITTEN, polylineGaps = null))
            assertEquals(REWRITTEN, awaitUntil { it.route?.polyline == REWRITTEN }.route?.polyline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheMapAtOnceAndKeepsItThroughAFailedReload() = runTest {
        recordExtra = SERVED_ROUTE
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the screen it opened while it's on the back stack.
                if (shown) RouteMapPresenter(mapScreen, FakeNavigator(mapScreen), r.feed, r.activities, r.profiles) else RouteMapState()
            }
        }) {
            awaitUntil { it.route != null && !it.content.isLoading }
            registry.saveAll()
            shown = false
            awaitUntil { it.route == null }
            offline = true
            shown = true
            assertEquals(SERVED_POLYLINE, awaitItem().route?.polyline, "no loading state in between")
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(SERVED_POLYLINE, failed.route?.polyline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backPops() = runTest {
        recordExtra = SERVED_ROUTE
        val navigator = FakeNavigator(mapScreen)
        map(repos(), navigator) {
            assertNotNull(awaitRoute()).eventSink(RouteMapEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** A route other than the served one. */
private const val REWRITTEN = "_p~iF~ps|U_ulLnnqC"
