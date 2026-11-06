package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.time.Instant

class GpxTest {
    private fun sample(lat: Double, lon: Double, alt: Double? = null) =
        LocationSample(lat, lon, alt, speed = null, accuracy = 5.0, time = Instant.fromEpochSeconds(0))

    @Test
    fun writesCoordinatesNearZeroWithoutAnExponent() {
        val gpx = Gpx.write("Run", "run", listOf(sample(51.5007, -0.0005, alt = 0.0001)))
        assertContains(gpx, """lat="51.5007" lon="-0.0005"""")
        assertContains(gpx, "<ele>0.0001</ele>")
        assertFalse(Regex("[0-9][eE]-?[0-9]").containsMatchIn(gpx))
    }

    @Test
    fun keepsEveryDigitOfAnOrdinaryCoordinate() {
        val gpx = Gpx.write("Run", "run", listOf(sample(51.500712345678, -0.12345678901234)))
        assertContains(gpx, """lat="51.500712345678" lon="-0.12345678901234"""")
    }

    @Test
    fun dropsCharactersXmlCantHoldFromTheName() {
        val gpx = Gpx.write("Morning\u0007 <run>", "run", emptyList())
        assertContains(gpx, "<name>Morning &lt;run&gt;</name>")
    }
}
