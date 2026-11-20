package io.github.solcott.activitytracker.desktop.location

import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.freedesktop.dbus.types.Variant

class DesktopPlaceLocatorTest {
    private val burlington = GeoPoint(44.4759, -73.2121)

    @Test
    fun theBackendsLocationIsTheLocation() = runTest {
        val locator = DesktopPlaceLocator { burlington }
        assertEquals(true, locator.permission(), "the OS asks, so the app may always try")
        assertEquals(burlington, locator.here())
    }

    @Test fun aFailingBackendIsNoLocation() = runTest { assertNull(DesktopPlaceLocator { error("location off") }.here()) }

    @Test fun aGarbledLocationIsNoLocation() = runTest { assertNull(DesktopPlaceLocator { GeoPoint(Double.NaN, 200.0) }.here()) }

    @Test
    fun aHangingBackendIsCutOffByTheCaller() = runTest {
        val locator = DesktopPlaceLocator { awaitCancellation() }
        assertNull(withTimeoutOrNull(1_000) { locator.here() })
    }

    @Test
    fun withNoBackendThereIsNoLocation() = runTest {
        val locator = DesktopPlaceLocator(null)
        assertFalse(locator.permission() == true)
        assertNull(locator.here())
    }

    @Test
    fun theMacHelpersAnswerReadsAsAPlace() {
        assertEquals(burlington, parseHelperOutput("""{"lat":44.4759,"lng":-73.2121}"""))
        assertNull(parseHelperOutput("""{"error":"denied"}"""))
        assertNull(parseHelperOutput("""{"lat":44.4759}"""))
        assertNull(parseHelperOutput("""{"lat":95.0,"lng":0.0}"""))
        assertNull(parseHelperOutput(""))
        assertNull(parseHelperOutput("not json"))
    }

    @Test
    fun thePowerShellLineReadsAsAPlace() {
        assertEquals(burlington, parsePowerShellOutput("44.4759,-73.2121\r\n"))
        assertEquals(burlington, parsePowerShellOutput("WARNING: something\r\n44.4759,-73.2121\r\n\r\n"), "the last line counts")
        // A localized number (a comma decimal) never parses as a place.
        assertNull(parsePowerShellOutput("44,4759,-73,2121"))
        assertNull(parsePowerShellOutput("Access is denied."))
        assertNull(parsePowerShellOutput(""))
        assertNull(parsePowerShellOutput("NaN,0"))
    }

    @Test
    fun geoCluesPropertiesReadAsAPlace() {
        val properties = mapOf("Latitude" to Variant(44.4759), "Longitude" to Variant(-73.2121), "Accuracy" to Variant(5000.0))
        assertEquals(burlington, readGeoClueLocation(properties))
        assertNull(readGeoClueLocation(mapOf("Latitude" to Variant(44.4759))))
        assertNull(readGeoClueLocation(mapOf("Latitude" to Variant(-91.0), "Longitude" to Variant(0.0))))
    }

    @Test
    fun aPlaceIsOnEarth() {
        assertTrue(GeoPoint(-90.0, 180.0).isOnEarth())
        assertFalse(GeoPoint(0.0, Double.POSITIVE_INFINITY).isOnEarth())
    }
}
