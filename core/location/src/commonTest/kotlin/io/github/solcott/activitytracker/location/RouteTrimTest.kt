package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RouteTrimTest {
    // 0.0001° of longitude at the equator is about 11.1 m; points every 11 m along it.
    private fun line(from: Int, to: Int) =
        (from..to).map { LocationSample(0.0, it * STEP, null, speed = null, accuracy = 5.0, time = Instant.fromEpochSeconds(it.toLong())) }

    private fun meters(a: LocationSample, b: LocationSample) = TrackMath.distanceMeters(a, b)

    private val origin = LocationSample(0.0, 0.0, null, speed = null, accuracy = null, time = Instant.fromEpochSeconds(0))

    private fun assertNoneInside(points: List<LocationSample>, circle: PrivacyCircle) = points.forEach {
        assertTrue(!circle.contains(it.latitude, it.longitude), "${it.latitude},${it.longitude} is inside")
    }

    @Test
    fun startAndEndInsideAZoneStopOnItsBoundary() {
        // An out-and-back from home: 0 → ~1.1 km → 0.
        val track = line(0, 100) + line(0, 99).reversed()
        val home = PrivacyCircle(0.0, 0.0, 200.0)
        val trimmed = TrackMath.trim(track, listOf(home))
        assertNoneInside(trimmed, home)
        assertTrue(abs(meters(origin, trimmed.first()) - 200) < 1, "starts on the boundary")
        assertTrue(abs(meters(origin, trimmed.last()) - 200) < 1, "ends on the boundary")
    }

    @Test
    fun aMiddlePassBecomesAChordBetweenEntryAndExit() {
        val track = line(0, 200)
        val zone = PrivacyCircle(0.0, 100 * STEP, 200.0)
        val trimmed = TrackMath.trim(track, listOf(zone))
        assertNoneInside(trimmed, zone)
        assertEquals(track.first(), trimmed.first())
        assertEquals(track.last(), trimmed.last())
        val gap = trimmed.zipWithNext().maxOf { (a, b) -> meters(a, b) }
        assertTrue(abs(gap - 400) < 2, "one chord across the zone, was $gap")
    }

    @Test
    fun hideEndsMeasuresAlongTheTrack() {
        val track = line(0, 200)
        val trimmed = TrackMath.trim(track, emptyList(), endsMeters = 400.0)
        assertTrue(abs(meters(track.first(), trimmed.first()) - 400) < 1)
        assertTrue(abs(meters(trimmed.last(), track.last()) - 400) < 1)
    }

    @Test
    fun zonesAndHiddenEndsTogether() {
        val track = line(0, 200)
        val zone = PrivacyCircle(0.0, 100 * STEP, 200.0)
        val trimmed = TrackMath.trim(track, listOf(zone), endsMeters = 400.0)
        assertNoneInside(trimmed, zone)
        assertTrue(abs(meters(track.first(), trimmed.first()) - 400) < 1)
        assertTrue(abs(meters(trimmed.last(), track.last()) - 400) < 1)
    }

    @Test
    fun piecesStartOnAZoneBoundary() {
        val track = line(0, 100) + line(0, 99).reversed()
        val home = PrivacyCircle(0.0, 0.0, 200.0)
        val pieces = TrackMath.trimPieces(track, listOf(home))
        assertEquals(1, pieces.size)
        assertNoneInside(pieces.single(), home)
        assertTrue(abs(meters(origin, pieces.single().first()) - 200) < 1, "starts on the boundary")
    }

    @Test
    fun aMiddlePassSplitsIntoTwoPiecesWithNothingBetween() {
        val track = line(0, 200)
        val zone = PrivacyCircle(0.0, 100 * STEP, 200.0)
        val pieces = TrackMath.trimPieces(track, listOf(zone))
        assertEquals(2, pieces.size)
        pieces.forEach { assertNoneInside(it, zone) }
        assertEquals(track.first(), pieces[0].first())
        assertEquals(track.last(), pieces[1].last())
        for (piece in pieces) {
            val gap = piece.zipWithNext().maxOf { (a, b) -> meters(a, b) }
            assertTrue(gap < 12, "no chord inside a piece, was $gap")
        }
        assertTrue(abs(meters(pieces[0].last(), pieces[1].first()) - 400) < 2, "the zone lies between the pieces")
        assertEquals(TrackMath.trim(track, listOf(zone)), pieces.flatten())
    }

    @Test
    fun piecesHideEnds() {
        val track = line(0, 200)
        val pieces = TrackMath.trimPieces(track, emptyList(), endsMeters = 400.0)
        assertEquals(1, pieces.size)
        assertTrue(abs(meters(track.first(), pieces.single().first()) - 400) < 1)
        assertTrue(abs(meters(pieces.single().last(), track.last()) - 400) < 1)
    }

    @Test
    fun piecesOfAFullyHiddenTrackAreEmpty() {
        val track = line(0, 10)
        assertEquals(emptyList(), TrackMath.trimPieces(track, listOf(PrivacyCircle(0.0, 5 * STEP, 1600.0))))
        assertEquals(emptyList(), TrackMath.trimPieces(emptyList(), emptyList()))
    }

    @Test
    fun piecesWithoutZonesAreTheWholeTrack() {
        val track = line(0, 10)
        assertEquals(listOf(track), TrackMath.trimPieces(track, emptyList()))
    }

    @Test
    fun everythingHiddenLeavesNothing() {
        val track = line(0, 10)
        assertEquals(emptyList(), TrackMath.trim(track, listOf(PrivacyCircle(0.0, 5 * STEP, 1600.0))))
        assertEquals(emptyList(), TrackMath.trim(track, emptyList(), endsMeters = 200.0))
    }

    @Test
    fun aTrackOutsideEveryZoneIsUnchanged() {
        val track = line(0, 50)
        assertEquals(track, TrackMath.trim(track, listOf(PrivacyCircle(10.0, 10.0, 1600.0))))
        assertEquals(track, TrackMath.trim(track, emptyList()))
    }

    @Test
    fun fewerThanTwoPoints() {
        assertEquals(emptyList(), TrackMath.trim(emptyList(), listOf(PrivacyCircle(0.0, 0.0, 200.0)), 200.0))
        assertEquals(emptyList(), TrackMath.trim(line(0, 0), listOf(PrivacyCircle(0.0, 0.0, 200.0))))
        assertEquals(line(0, 0), TrackMath.trim(line(0, 0), listOf(PrivacyCircle(1.0, 1.0, 200.0))))
    }

    @Test
    fun offsetsStayWithinHalfTheRadius() {
        val random = Random(42)
        repeat(1_000) {
            val (lat, lon) = PrivacyCircle.offsetCenter(51.5, -0.12, 400.0, random)
            assertTrue(TrackMath.distanceMeters(51.5, -0.12, lat, lon) <= 200.0 + 1e-6)
        }
    }

    @Test
    fun e7RoundTripsWithinACentimeter() {
        val lat = 51.501_234_56
        val lon = -0.127_654_32
        val circle = PrivacyCircle.fromE7(PrivacyCircle.toE7(lat), PrivacyCircle.toE7(lon), 400)
        assertTrue(TrackMath.distanceMeters(lat, lon, circle.latitude, circle.longitude) < 0.01)
    }

    // Sparse samples: two points a kilometre apart on the equator, east-west, `seconds` apart.
    private fun sample(eastMeters: Double, northMeters: Double = 0.0, seconds: Long = 0) =
        LocationSample(northMeters / METERS_PER_DEGREE, eastMeters / METERS_PER_DEGREE, null, null, null, Instant.fromEpochSeconds(seconds))

    private fun zone(eastMeters: Double, northMeters: Double = 0.0, radius: Double = 200.0) =
        PrivacyCircle(northMeters / METERS_PER_DEGREE, eastMeters / METERS_PER_DEGREE, radius)

    private fun assertOnBoundary(point: LocationSample, circle: PrivacyCircle) {
        val distance = TrackMath.distanceMeters(circle.latitude, circle.longitude, point.latitude, point.longitude)
        assertTrue(abs(distance - circle.radiusMeters) < 1, "on the boundary, was $distance from the center")
    }

    @Test
    fun aLineBetweenSparseSamplesThroughAZoneIsCut() {
        val track = listOf(sample(-500.0), sample(500.0, seconds = 120))
        val home = zone(0.0)
        val pieces = TrackMath.trimPieces(track, listOf(home))
        assertEquals(2, pieces.size)
        pieces.forEach { assertNoneInside(it, home) }
        assertEquals(track.first(), pieces[0].first())
        assertOnBoundary(pieces[0].last(), home)
        assertOnBoundary(pieces[1].first(), home)
        assertEquals(track.last(), pieces[1].last())
        assertEquals(TrackMath.trim(track, listOf(home)), pieces.flatten())
    }

    @Test
    fun aLinePassingBesideAZoneStaysWhole() {
        val track = listOf(sample(-500.0), sample(500.0, seconds = 120))
        assertEquals(listOf(track), TrackMath.trimPieces(track, listOf(zone(0.0, northMeters = 210.0))))
    }

    @Test
    fun aLineThroughTwoZonesGivesThreePieces() {
        val track = listOf(sample(-1000.0), sample(1000.0, seconds = 240))
        val zones = listOf(zone(-400.0), zone(400.0))
        val pieces = TrackMath.trimPieces(track, zones)
        assertEquals(3, pieces.size)
        pieces.forEach { piece -> zones.forEach { assertNoneInside(piece, it) } }
        assertOnBoundary(pieces[1].first(), zones[0])
        assertOnBoundary(pieces[1].last(), zones[1])
    }

    @Test
    fun aLineFromInsideOneZoneThroughAnotherIsCutAtBoth() {
        val track = listOf(sample(0.0), sample(1000.0, seconds = 120))
        val home = zone(0.0)
        val shop = zone(600.0)
        val pieces = TrackMath.trimPieces(track, listOf(home, shop))
        assertEquals(2, pieces.size)
        assertOnBoundary(pieces[0].first(), home)
        assertOnBoundary(pieces[0].last(), shop)
        assertOnBoundary(pieces[1].first(), shop)
        assertEquals(track.last(), pieces[1].last())
    }

    @Test
    fun overlappingZonesOnALineAreOneGap() {
        val track = listOf(sample(-1000.0), sample(1000.0, seconds = 240))
        val pieces = TrackMath.trimPieces(track, listOf(zone(-100.0), zone(100.0)))
        assertEquals(2, pieces.size)
        assertTrue(abs(meters(pieces[0].last(), pieces[1].first()) - 600) < 2, "one gap across both zones")
    }

    private companion object {
        const val STEP = 0.0001

        // On the equator, by the haversine distance's earth radius.
        const val METERS_PER_DEGREE = 6_371_000.0 * kotlin.math.PI / 180
    }
}
