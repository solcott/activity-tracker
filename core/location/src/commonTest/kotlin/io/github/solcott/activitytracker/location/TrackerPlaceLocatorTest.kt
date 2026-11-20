package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class TrackerPlaceLocatorTest {
    private class Tracker(
        override val isSupported: Boolean = true,
        private val granted: Boolean = true,
        private val updates: Flow<LocationSample> = emptyFlow(),
    ) : LocationTracker {
        override fun hasPermission(): Boolean = granted

        override fun updates(): Flow<LocationSample> = updates
    }

    private fun sample(lat: Double, lng: Double) = LocationSample(lat, lng, null, null, null, Instant.fromEpochSeconds(0))

    @Test
    fun theFirstFixIsTheLocation() = runTest {
        val locator = TrackerPlaceLocator(Tracker(updates = flowOf(sample(44.48, -73.21), sample(1.0, 2.0))))
        assertEquals(GeoPoint(44.48, -73.21), locator.here())
    }

    @Test fun noFixIsNoLocation() = runTest { assertNull(TrackerPlaceLocator(Tracker()).here()) }

    @Test
    fun aFailingTrackerIsNoLocation() = runTest { assertNull(TrackerPlaceLocator(Tracker(updates = flow { error("GPS off") })).here()) }

    @Test
    fun permissionFollowsTheTracker() {
        assertEquals(false, TrackerPlaceLocator(Tracker(isSupported = false)).permission())
        assertEquals(true, TrackerPlaceLocator(Tracker(granted = true)).permission())
        assertNull(TrackerPlaceLocator(Tracker(granted = false)).permission())
    }
}
