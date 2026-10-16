package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The owner's full route on their own activity (`profile/privacy-zones`), and adding a privacy zone at its start or end. */
class ActivityDetailRouteTest : ActivityDetailTestBase() {
    private val start = Instant.parse("2026-09-30T10:00:00Z")
    private val route =
        (0..100).map { LocationSample(51.5, -0.12 + it * 0.0001, null, speed = null, accuracy = 5.0, time = start + it.seconds) }

    @Test
    fun theOwnerSeesTheFullRouteFromTheirTrack() = runTest {
        author = staleAlice
        track = route
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.ownRoute != null }.loaded()!!
            assertEquals(ActivityDraft.polylineOf(route), loaded.ownRoute)
            assertTrue(loaded.canHideRouteEnds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesActivityShowsOnlyItsPolyline() = runTest {
        track = route
        detail(repos()) {
            val loaded = awaitLoaded()
            assertFalse(loaded.isOwn)
            assertNull(loaded.ownRoute)
            assertFalse(loaded.canHideRouteEnds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableTrackLeavesThePolyline() = runTest {
        author = staleAlice
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.isOwn == true }.loaded()!!
            assertNull(loaded.ownRoute)
            assertFalse(loaded.canHideRouteEnds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hideThisStartAddsAZoneNearTheTracksFirstPoint() = runTest {
        author = staleAlice
        track = route
        detail(repos()) {
            awaitUntil { it.loaded()?.canHideRouteEnds == true }.eventSink(ActivityDetailEvent.HideStart)
            val done = awaitUntil { it.loaded()?.let { l -> l.zoneAdded || l.routeError != null } == true }.loaded()!!
            assertNull(done.routeError)
            cancelAndIgnoreRemainingEvents()
        }
        val zone = AtJson.decodeFromString(PrivacyZonesRecord.serializer(), privacyZones!!).zones!!.single()
        assertEquals(400, zone.radiusMeters)
        val first = route.first()
        assertTrue(TrackMath.distanceMeters(first.latitude, first.longitude, zone.latitudeE7 / 1e7, zone.longitudeE7 / 1e7) <= 200.0)
    }
}
