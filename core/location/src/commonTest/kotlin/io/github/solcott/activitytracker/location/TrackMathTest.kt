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
}
