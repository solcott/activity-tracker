package io.github.solcott.activitytracker.app.browse

import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PlaceLocator
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Where map browse opens (segments/segment-browse): the location, the latest activity, nowhere, and a location that answers late. */
class MapBrowseLocateTest {
    private val here = GeoPoint(44.4759, -73.2121)
    private val stowe = GeoPoint(44.4654, -72.6874)

    /** A location that answers [answer] once released, held [hold] before browsing opens at its fallback. */
    private class Locator(override val hold: Duration = 2.seconds, private val permission: Boolean? = true) : PlaceLocator {
        val answer = CompletableDeferred<GeoPoint?>()

        override fun permission(): Boolean? = permission

        override suspend fun here(): GeoPoint? = answer.await()
    }

    private fun viewportAt(at: GeoPoint, halfSpan: Double = 0.02) =
        MapViewport(
            minLat = e7(at.lat - halfSpan),
            minLng = e7(at.lng - halfSpan),
            maxLat = e7(at.lat + halfSpan),
            maxLng = e7(at.lng + halfSpan),
            centerLat = e7(at.lat),
            centerLng = e7(at.lng),
        )

    private fun e7(degrees: Double) = (degrees * 1e7).roundToInt()

    private val MapBrowse.cameraAt
        get() = camera?.let { GeoPoint(it.lat, it.lng) }

    @Test
    fun aLocationWithinTheHoldOpensThere() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(1.seconds)
        locator.answer.complete(here)
        advanceUntilIdle()
        assertEquals(here, browse.cameraAt)
        assertEquals(BrowseStart.Placed, browse.start)
    }

    @Test
    fun aRefusedLocationOpensAtTheLatestActivity() = runTest {
        val browse = MapBrowse(permission = false)
        browse.locate(Locator(permission = false)) { stowe }
        assertEquals(stowe, browse.cameraAt)
    }

    @Test
    fun noLocationAndNoActivityOpensNowhere() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { null } }
        locator.answer.complete(null)
        advanceUntilIdle()
        assertEquals(BrowseStart.Nowhere, browse.start)
        assertNull(browse.camera)
    }

    @Test
    fun aLateLocationMovesTheUntouchedMap() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(stowe, browse.cameraAt, "opened at the latest activity once the hold passed")
        // The map arriving where it was sent isn't the athlete moving it, nor is its own first view before it got there.
        browse.viewportChanged(viewportAt(GeoPoint(0.0, 0.0), halfSpan = 40.0))
        browse.viewportChanged(viewportAt(stowe))
        locator.answer.complete(here)
        advanceUntilIdle()
        assertEquals(here, browse.cameraAt)
    }

    @Test
    fun aLateLocationLeavesAMapTheAthleteMoved() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(3.seconds)
        runCurrent()
        browse.viewportChanged(viewportAt(stowe))
        browse.viewportChanged(viewportAt(GeoPoint(44.26, -72.58)))
        locator.answer.complete(here)
        advanceUntilIdle()
        assertEquals(stowe, browse.cameraAt, "the map stays where the athlete put it")
    }

    @Test
    fun aLateLocationMovesAMapThatOpenedNowhereUntilTheAthleteZoomsIn() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { null } }
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(BrowseStart.Nowhere, browse.start)
        locator.answer.complete(here)
        advanceUntilIdle()
        assertEquals(here, browse.cameraAt)
        assertEquals(BrowseStart.Placed, browse.start)

        val zoomedIn = Locator()
        val other = MapBrowse(zoomedIn.permission())
        launch { other.locate(zoomedIn) { null } }
        advanceTimeBy(3.seconds)
        runCurrent()
        other.viewportChanged(viewportAt(stowe))
        zoomedIn.answer.complete(here)
        advanceUntilIdle()
        assertNull(other.camera, "the athlete found somewhere themselves")
    }

    @Test
    fun aPanWhileLocatingKeepsTheMapWhereTheAthletePutIt() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(1.seconds)
        // The map's own first view, then the athlete panning to Montpelier before the hold passes.
        browse.viewportChanged(viewportAt(GeoPoint(0.0, 0.0), halfSpan = 0.5))
        browse.viewportChanged(viewportAt(GeoPoint(44.26, -72.58)))
        assertEquals(BrowseStart.Placed, browse.start)
        advanceTimeBy(3.seconds)
        runCurrent()
        assertNull(browse.camera, "the fallback doesn't move a map the athlete moved")
        locator.answer.complete(here)
        advanceUntilIdle()
        assertNull(browse.camera, "nor does a late location")
    }

    @Test
    fun theMapsOwnViewsWhileLocatingDontStopTheFallback() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(1.seconds)
        browse.viewportChanged(viewportAt(GeoPoint(0.0, 0.0), halfSpan = 40.0))
        // The same centre again, as a resize reports it.
        browse.viewportChanged(viewportAt(GeoPoint(0.0, 0.0), halfSpan = 30.0))
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(stowe, browse.cameraAt)
    }

    @Test
    fun aLocationAfterTheLimitIsIgnored() = runTest {
        val locator = Locator()
        val browse = MapBrowse(locator.permission())
        launch { browse.locate(locator) { stowe } }
        advanceTimeBy(LATE_LOCATION_LIMIT + 1.seconds)
        locator.answer.complete(here)
        advanceUntilIdle()
        assertEquals(stowe, browse.cameraAt)
    }
}
