package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** How closely a published polyline follows its track, and the record's size limit (feed/route-line-detail). */
class PolylineDetailTest {
    private val start = Instant.parse("2026-10-27T07:00:00Z")

    /**
     * Up and back down a slope [turns] times through hairpins 10 m across: straights of [straight] meters joined by half circles, a
     * position every meter and second, the way a trail's switchbacks are ridden.
     */
    private fun switchbacks(turns: Int, straight: Double = 40.0): List<LocationSample> {
        val points = mutableListOf<Pair<Double, Double>>()
        var y = 0.0
        for (turn in 0 until turns) {
            val east = turn % 2 == 0
            for (i in 0..straight.toInt()) points += (if (east) i.toDouble() else straight - i) to y
            // Half a circle of radius 5 m, a point every meter or so.
            for (k in 1 until HAIRPIN_POINTS) {
                val a = PI * k / HAIRPIN_POINTS
                val x = if (east) straight + RADIUS * sin(a) else -RADIUS * sin(a)
                points += x to y + RADIUS - RADIUS * cos(a)
            }
            y += 2 * RADIUS
        }
        return points.mapIndexed { i, (x, y) ->
            LocationSample(y / METERS_PER_DEGREE, x / METERS_PER_DEGREE, null, null, null, start + i.seconds)
        }
    }

    private fun decoded(polyline: SegmentedPolyline) = TrackMath.decodePolyline(polyline.polyline).map { (lat, lng) -> GeoPoint(lat, lng) }

    private fun furthest(track: List<LocationSample>, line: List<GeoPoint>) = track.maxOf {
        SegmentGeometry.distanceToLineMeters(GeoPoint(it.latitude, it.longitude), line)
    }

    @Test
    fun aPolylineFollowsSwitchbacksWithinTwoMeters() {
        val track = switchbacks(turns = 6)
        val line = decoded(ActivityDraft.polylineOf(track)!!)
        assertTrue(
            furthest(track, line) <= SegmentedPolyline.TOLERANCE_M + PRECISION_5_M,
            "a track point was ${furthest(track, line)} m off",
        )
        // Each hairpin keeps its turn, not just the ends of its straights; 5 m kept only those.
        val coarse = TrackMath.simplify(track, 5.0)
        assertTrue(line.size > coarse.size, "${line.size} points, no more than ${coarse.size} at 5 m")
    }

    @Test
    fun breaksAndTrimsAreUnchanged() {
        val track = switchbacks(turns = 6)
        val later = track.last().time + 10.seconds * 60
        val resumed = switchbacks(turns = 2).map { it.copy(longitude = it.longitude + 0.03, time = later + (it.time - start)) }
        val polyline = ActivityDraft.polylineOf(track + resumed)!!
        assertEquals(1, polyline.gaps?.size)
        val trimmed = RouteTrim.Known(PrivacySettings(emptyList(), hideEndsMeters = 50), null).polylineOf(track)!!
        val first = decoded(trimmed).first()
        val from = track.first()
        assertTrue(
            SegmentGeometry.distanceMeters(GeoPoint(from.latitude, from.longitude), first) >= 30.0,
            "the hidden start is still left out",
        )
    }

    @Test
    fun aPolylineTooLongForARecordIsCoarsenedToFit() {
        // About 150 km of switchbacks at a position a meter: far more than 50,000 characters at 2 m.
        val track = switchbacks(turns = 2_500)
        val polyline = ActivityDraft.polylineOf(track)!!
        assertTrue(polyline.polyline.length <= SegmentedPolyline.MAX_CHARS, "${polyline.polyline.length} characters")
        assertTrue(polyline.polyline.length > SegmentedPolyline.MAX_CHARS / 4, "no coarser than it needs to be")
    }

    @Test
    fun theOwnersLineIsNeverCutDown() {
        val track = switchbacks(turns = 2_500)
        val own = SegmentedPolyline.of(listOf(track), SegmentedPolyline.OWN_TOLERANCE_M, maxChars = Int.MAX_VALUE)!!
        assertEquals(TrackMath.encodePolyline(TrackMath.simplify(track, SegmentedPolyline.OWN_TOLERANCE_M)), own.polyline)
        assertTrue(own.polyline.length > ActivityDraft.polylineOf(track)!!.polyline.length, "finer than the published one")
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
        const val RADIUS = 5.0
        const val HAIRPIN_POINTS = 16

        /** The most a precision-5 encoding moves a point. */
        const val PRECISION_5_M = 0.8
    }
}
