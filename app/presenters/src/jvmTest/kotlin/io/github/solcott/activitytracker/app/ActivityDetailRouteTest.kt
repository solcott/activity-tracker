package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
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
            val loaded = awaitUntil { it.loaded()?.route?.ends != null }.loaded()!!
            assertEquals(ActivityDraft.polylineOf(route)?.polyline, loaded.route?.polyline)
            assertTrue(loaded.canHideRouteEnds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theOwnersLineKeepsTheTrackWithinOneMeter() = runTest {
        author = staleAlice
        // A wiggle of 0.7 m either side, 1.4 m across: finer than the published polyline keeps, coarser than the owner's map.
        val wiggly = route.mapIndexed { i, p -> p.copy(latitude = p.latitude + if (i % 2 == 0) 0.0000063 else -0.0000063) }
        track = wiggly
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.route?.ends != null }.loaded()!!
            val line = TrackMath.decodePolyline(loaded.route!!.polyline).map { (lat, lng) -> GeoPoint(lat, lng) }
            val furthest = wiggly.maxOf { SegmentGeometry.distanceToLineMeters(GeoPoint(it.latitude, it.longitude), line) }
            assertTrue(furthest <= 1.0 + PRECISION_5_M, "a track point was $furthest m off")
            assertTrue(line.size > TrackMath.decodePolyline(ActivityDraft.polylineOf(wiggly)!!.polyline).size, "finer than published")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withNothingHiddenTheMarkersAreAtTheTracksEnds() = runTest {
        author = staleAlice
        track = route
        detail(repos()) {
            val ends = awaitUntil { it.loaded()?.route?.ends?.start != null }.loaded()!!.route!!.ends!!
            assertEquals(
                RouteEnds(route.first().let { it.latitude to it.longitude }, route.last().let { it.latitude to it.longitude }),
                ends,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hiddenEndsMoveTheMarkersOffTheTracksEnds() = runTest {
        author = staleAlice
        track = route
        privacyZones = AtJson.encodeToString(PrivacyZonesRecord.serializer(), PrivacyZonesRecord("3m3ygsw4ngk2c", hideEndsMeters = 200))
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.route?.ends?.start != null }.loaded()!!
            // The line is still the whole track; only its markers move to where the route others see starts and ends.
            assertEquals(ActivityDraft.polylineOf(route)?.polyline, loaded.route?.polyline)
            val (start, end) = loaded.route!!.ends!!
            val first = route.first()
            val last = route.last()
            assertEquals(
                200.0,
                TrackMath.distanceMeters(first.latitude, first.longitude, start!!.first, start.second),
                absoluteTolerance = 8.0,
            )
            assertEquals(200.0, TrackMath.distanceMeters(last.latitude, last.longitude, end!!.first, end.second), absoluteTolerance = 8.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRouteHiddenWhollyHasNoStartOrEndMarker() = runTest {
        author = staleAlice
        track = route
        privacyZones = AtJson.encodeToString(PrivacyZonesRecord.serializer(), PrivacyZonesRecord("3m3ygsw4ngk2c", hideEndsMeters = 5000))
        detail(repos()) {
            // Wait for the zones' read: the ends are hidden until then too.
            val loaded = awaitUntil { it.loaded()?.route?.ends != null }.loaded()!!
            assertEquals(RouteEnds.HIDDEN, loaded.route?.ends)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesActivityShowsOnlyItsPolyline() = runTest {
        track = route
        detail(repos()) {
            val loaded = awaitLoaded()
            assertFalse(loaded.isOwn)
            assertNull(loaded.route)
            assertFalse(loaded.canHideRouteEnds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesMapDrawsTheServedPolylineMarkedAtItsOwnEnds() = runTest {
        track = route
        recordExtra = SERVED_ROUTE
        detail(repos()) {
            val loaded = awaitLoaded()
            assertEquals(ShownRoute(SERVED_POLYLINE, persistentListOf(1), ends = null), loaded.route)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableTrackLeavesThePolyline() = runTest {
        author = staleAlice
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.isOwn == true }.loaded()!!
            assertNull(loaded.route)
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

    internal companion object {
        /** The most a precision-5 encoding moves a point: half of 1e-5 degrees on each axis. */
        const val PRECISION_5_M = 0.8

        /** A trimmed polyline of three points, as the AppView serves it. */
        const val SERVED_POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

        /** [SERVED_POLYLINE] on the activity's record, broken after its second point. */
        const val SERVED_ROUTE = """"polyline":"$SERVED_POLYLINE","polylineGaps":[1],"""
    }
}
