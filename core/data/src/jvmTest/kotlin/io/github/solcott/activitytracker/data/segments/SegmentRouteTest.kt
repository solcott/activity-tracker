package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Cutting a segment from the privacy-trimmed pieces of an activity's route (segments/segment-creation). */
class SegmentRouteTest {
    private val start = Instant.parse("2026-10-05T08:00:00Z")

    /** About 3.3 km east along the equator, a sample every ~11 m. */
    private val route =
        (0..300).map { LocationSample(0.0, it * 0.0001, 12.0, speed = null, accuracy = 5.0, time = start + (it * 4).seconds) }

    /** A zone 200 m around a point ~1.67 km along, and the first and last 100 m hidden. */
    private val zone = PrivacyZone(latitudeE7 = 0, longitudeE7 = 150_000, radiusMeters = 200)
    private val settings = PrivacySettings(listOf(zone), hideEndsMeters = 100)
    private val segments = SegmentRoute(settings.pieces(route))

    private fun meters(a: GeoPoint, b: GeoPoint) = SegmentGeometry.distanceMeters(a, b)

    private val zoneCenter = GeoPoint(0.0, 0.015)
    private val routeStart = GeoPoint(0.0, 0.0)

    @Test
    fun theRouteLeavesOutTheZoneAndTheHiddenEnds() {
        // 3.34 km, less 2 × 100 m of hidden ends and the zone's 400 m.
        assertTrue(abs(segments.lengthMeters - 2_740) < 30, "length ${segments.lengthMeters}")
        assertEquals(1, segments.gaps.size)
        assertTrue(abs(segments.gaps.single() - 1_370) < 20, "gap at ${segments.gaps}")
    }

    @Test
    fun aStretchInOnePieceIsCut() {
        val cut = assertIs<SegmentSelection.Ok>(segments.select(200.0, 1_000.0, SegmentSportGroup.RUN))
        assertTrue(abs(cut.meters - 800) < 1)
        assertTrue(abs(SegmentGeometry.lengthMeters(cut.line) - 800) < 1)
        assertTrue(cut.line.all { meters(it, zoneCenter) > 200 }, "outside the zone")
        assertTrue(cut.line.all { meters(it, routeStart) > 100 }, "outside the hidden start")
        assertEquals(cut.line.size, SegmentGeometry.decodePolyline(cut.encoded)!!.size)
        // Picking the end first gives the same stretch, still in the route's direction.
        assertEquals(cut.line, assertIs<SegmentSelection.Ok>(segments.select(1_000.0, 200.0, SegmentSportGroup.RUN)).line)
    }

    @Test
    fun aStretchAcrossTheZoneIsRefused() {
        assertEquals(SegmentSelection.CrossesGap, segments.select(1_000.0, 1_800.0, SegmentSportGroup.RUN))
    }

    @Test
    fun aStretchStartingAtTheGapBelongsToTheNextPiece() {
        val gap = segments.gaps.single()
        val cut = assertIs<SegmentSelection.Ok>(segments.select(gap, gap + 500, SegmentSportGroup.RUN))
        assertTrue(cut.line.all { meters(it, zoneCenter) > 199 })
    }

    @Test
    fun lengthBoundsDependOnTheSportGroup() {
        assertIs<SegmentSelection.Ok>(segments.select(200.0, 550.0, SegmentSportGroup.RUN))
        assertEquals(SegmentSelection.TooShort(500), segments.select(200.0, 550.0, SegmentSportGroup.RIDE))
        assertEquals(SegmentSelection.TooShort(200), segments.select(200.0, 300.0, SegmentSportGroup.RUN))
    }

    @Test
    fun nothingLeftToCut() {
        val hidden = SegmentRoute(PrivacySettings(hideEndsMeters = 5_000).pieces(route))
        assertTrue(hidden.isEmpty)
        assertEquals(SegmentSelection.CrossesGap, hidden.select(0.0, 300.0, SegmentSportGroup.RUN))
    }

    @Test
    fun aSliderRoundingPastAPiecesEndStaysInIt() {
        val gap = segments.gaps.single()
        val cut = assertIs<SegmentSelection.Ok>(segments.select(200.0, gap + 0.3, SegmentSportGroup.RUN))
        assertTrue(abs(cut.meters - (gap - 200)) < 0.01)
        assertTrue(cut.line.all { meters(it, zoneCenter) > 199 })
    }
}
