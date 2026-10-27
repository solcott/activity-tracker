package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PrivacySettingsTest {
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    /** About 3.3 km east along the equator from (0, 0), a point every 11 m. */
    private val route = (0..300).map { at(it) }

    private fun at(i: Int, time: Instant = start + (i * 4).seconds) = LocationSample(0.0, i * 0.0001, null, null, 5.0, time)

    /** 200 m zones around the route's start and its end. */
    private val home = PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 200)
    private val work = PrivacyZone(latitudeE7 = 0, longitudeE7 = 300_000, radiusMeters = 200)

    @Test
    fun noZonesShowTheRoutesOwnEnds() {
        assertEquals(route.first() to route.last(), PrivacySettings.NONE.shownEnds(route))
    }

    @Test
    fun theEndsAreWhereTheRouteLeavesAndEntersItsZones() {
        val (first, last) = PrivacySettings(listOf(home, work)).shownEnds(route)!!
        val origin = at(0)
        assertTrue(TrackMath.distanceMeters(origin, first) in 199.0..215.0)
        assertTrue(TrackMath.distanceMeters(route.last(), last) in 199.0..215.0)
    }

    @Test
    fun hiddenEndsMoveTheMarkersAlongTheRoute() {
        val (first, last) = PrivacySettings(hideEndsMeters = 500).shownEnds(route)!!
        assertTrue(TrackMath.distanceMeters(route.first(), first) in 499.0..515.0)
        assertTrue(TrackMath.distanceMeters(route.last(), last) in 499.0..515.0)
    }

    @Test
    fun aRouteAllInsideAZoneHasNoEnds() {
        assertNull(PrivacySettings(listOf(home)).shownEnds(route.take(10)))
    }

    @Test
    fun theEndsAreThoseOfThePublishedPolyline() {
        // Out from home, then, an hour later, a second recording that ends back at home.
        val back = route.reversed().mapIndexed { i, p -> p.copy(time = route.last().time + 60.minutes + (i * 4).seconds) }
        val settings = PrivacySettings(listOf(home), hideEndsMeters = 500)
        val trim = RouteTrim.Known(settings, null)
        assertSameEnds(trim.polylineOf(route), settings.shownEnds(route))
        assertSameEnds(trim.segmentedPolylineOf(route + back), settings.shownEnds(route + back, combined = true))
    }

    @Test
    fun aZoneBetweenSparseSamplesSplitsTheSegmentTrack() {
        // A sample every 1.1 km, and a zone mid-route that none of them falls in.
        val sparse = listOf(0, 100, 200, 300).map { at(it) }
        val shop = PrivacyZone(latitudeE7 = 0, longitudeE7 = 150_000, radiusMeters = 200)
        val pieces = PrivacySettings(listOf(shop)).pieces(sparse)
        assertEquals(2, pieces.size)
        val center = at(150)
        for (point in pieces.flatten()) assertTrue(TrackMath.distanceMeters(center, point) >= 199.0)
        assertTrue(TrackMath.distanceMeters(center, pieces[0].last()) in 199.0..201.0)
        assertTrue(TrackMath.distanceMeters(center, pieces[1].first()) in 199.0..201.0)
    }

    private fun assertSameEnds(published: SegmentedPolyline?, ends: Pair<LocationSample, LocationSample>?) {
        val line = TrackMath.decodePolyline(published!!.polyline)
        val (first, last) = ends!!
        for ((point, end) in listOf(line.first() to first, line.last() to last)) {
            assertEquals(point.first, end.latitude, absoluteTolerance = 1e-5)
            assertEquals(point.second, end.longitude, absoluteTolerance = 1e-5)
        }
    }
}
