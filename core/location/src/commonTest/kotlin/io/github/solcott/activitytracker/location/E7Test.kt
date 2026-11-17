package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals

class E7Test {
    @Test
    fun degreesRoundToTheNearestE7() {
        assertEquals(445_123_457, 44.51234567.toE7())
        assertEquals(-731_234_567, (-73.12345674).toE7())
        assertEquals(0, 0.00000004.toE7())
        assertEquals(1_800_000_000, 180.0.toE7())
    }

    @Test
    fun e7ReadsBackAsDegrees() {
        assertEquals(44.5123457, 445_123_457.e7ToDegrees(), 1e-12)
        assertEquals(GeoPoint(-90.0, 180.0), pointE7(-900_000_000, 1_800_000_000))
        val lat = 27.9876543
        assertEquals(lat, lat.toE7().e7ToDegrees(), 1e-12)
    }
}
