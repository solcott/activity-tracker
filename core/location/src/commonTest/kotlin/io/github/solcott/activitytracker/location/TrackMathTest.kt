package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TrackMathTest {
    private fun sample(lat: Double, lon: Double, alt: Double? = null, sec: Long = 0) =
        LocationSample(
            lat,
            lon,
            alt,
            speed = null,
            accuracy = 5.0,
            time = Instant.fromEpochSeconds(sec),
        )

    @Test
    fun encodesGooglesReferencePolyline() {
        val points = listOf(sample(38.5, -120.2), sample(40.7, -120.95), sample(43.252, -126.453))
        val encoded = TrackMath.encodePolyline(points)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encoded)
        assertEquals(38.5 to -120.2, TrackMath.decodePolyline(encoded).first())
    }

    @Test
    fun haversineMatchesKnownDistance() {
        // One degree of latitude ≈ 111.2 km.
        val d = TrackMath.distanceMeters(sample(0.0, 0.0), sample(1.0, 0.0))
        assertTrue(abs(d - 111_195) < 100, "was $d")
    }

    @Test
    fun simplifyDropsCollinearPoints() {
        val line = (0..100).map { sample(0.0, it * 0.0001) }
        assertEquals(2, TrackMath.simplify(line).size)
    }

    @Test
    fun simplifyAtTwoMetersKeepsSwitchbacks() {
        val trail = TomSawyer.samples
        assertEquals(229, trail.size)
        val kept = TrackMath.simplify(trail, 2.0).map { GeoPoint(it.latitude, it.longitude) }
        val furthest = TomSawyer.points.maxOf { SegmentGeometry.distanceToLineMeters(it, kept) }
        assertTrue(furthest <= 2.0, "a trail point was $furthest m off")
        assertTrue(kept.size > TrackMath.simplify(trail, 5.0).size, "finer than 5 m")
    }

    @Test
    fun simplifyToFitKeepsTheStartToleranceWhenItFits() {
        val trail = TomSawyer.samples
        val fitted = TrackMath.simplifyToFit(listOf(trail), 1.0, Int.MAX_VALUE, ::encodeAll)
        assertEquals(TrackMath.simplify(trail, 1.0), fitted.single())
    }

    @Test
    fun simplifyToFitCoarsensOnlyAsFarAsItMust() {
        val trail = TomSawyer.samples
        val budget = encodeAll(listOf(TrackMath.simplify(trail, 3.0))).length
        val fitted = TrackMath.simplifyToFit(listOf(trail), 0.5, budget, ::encodeAll)
        assertTrue(encodeAll(fitted).length <= budget)
        // 0.5 m doubles to 4 m, the first that fits; never as coarse as twice that.
        assertEquals(TrackMath.simplify(trail, 4.0), fitted.single())
        assertTrue(fitted.single().size >= TrackMath.simplify(trail, 8.0).size)
    }

    @Test
    fun simplifyToFitSharesOneToleranceAcrossPieces() {
        val trail = TomSawyer.samples
        val pieces = listOf(trail.subList(0, 120), trail.subList(120, trail.size))
        val budget = encodeAll(pieces.map { TrackMath.simplify(it, 2.0) }).length
        val fitted = TrackMath.simplifyToFit(pieces, 1.0, budget, ::encodeAll)
        assertEquals(pieces.map { TrackMath.simplify(it, 2.0) }, fitted)
    }

    @Test
    fun simplifyToFitStopsAtTheEnds() {
        val fitted = TrackMath.simplifyToFit(listOf(TomSawyer.samples), 1.0, 1, ::encodeAll)
        assertEquals(2, fitted.single().size)
    }

    private fun encodeAll(pieces: List<List<LocationSample>>) = TrackMath.encodePolyline(pieces.flatten())
}
