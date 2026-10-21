package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
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
import io.github.solcott.activitytracker.location.LocationSample
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
            assertEquals("Morning run", state.title)
            assertEquals("run", state.activityType?.sport)
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
    fun aFailedLoadShowsAnError() = runTest {
        offline = true
        map(repos()) {
            val state = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.LoadActivity, state.error?.kind)
            assertNull(state.route)
            assertFalse(state.isLoading)
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
