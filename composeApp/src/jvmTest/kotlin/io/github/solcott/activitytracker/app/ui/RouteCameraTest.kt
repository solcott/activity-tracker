package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.BoundingBox

class RouteCameraTest {
    private val card = DpSize(400.dp, 300.dp)

    /** The room left for the route in [card] inside the default margin. */
    private val room = 400.0 - 48 to 300.0 - 48

    /** The route's width and height in dp at [camera]'s zoom. */
    private fun drawnSize(bounds: BoundingBox, camera: CameraPosition): Pair<Double, Double> {
        fun y(lat: Double) = (1 - ln(tan(PI / 4 + lat * PI / 360)) / PI) / 2
        val world = 512 * 2.0.pow(camera.zoom)
        return (bounds.east - bounds.west) / 360 * world to (y(bounds.south) - y(bounds.north)) * world
    }

    @Test
    fun anEastWestRouteAtTheEquatorFillsTheWidth() {
        val bounds = BoundingBox(west = 0.0, south = 0.0, east = 0.01, north = 0.0)
        val camera = fitCamera(bounds, card)

        assertEquals(0.005, camera.center.longitude, 1e-9)
        assertEquals(0.0, camera.center.latitude, 1e-9)
        // 352 dp for 0.01° of a 512 dp world: log2(352 × 360 / (0.01 × 512)).
        assertEquals(14.595, camera.zoom, 1e-3)
        assertEquals(room.first, drawnSize(bounds, camera).first, 1e-6)
    }

    @Test
    fun aTallRouteFillsTheHeight() {
        val bounds = BoundingBox(west = -72.6, south = 44.0, east = -72.59, north = 44.5)
        val camera = fitCamera(bounds, card)

        val (width, height) = drawnSize(bounds, camera)
        assertEquals(room.second, height, 1e-6)
        assertTrue(width < room.first, "$width dp wide")
        assertEquals(-72.595, camera.center.longitude, 1e-9)
        // Mercator stretches the north, so the middle of the frame is a little north of the middle latitude.
        assertTrue(camera.center.latitude in 44.25..44.26, "${camera.center.latitude}")
    }

    @Test
    fun aSinglePointIsDrawnAtTheClosestZoom() {
        val bounds = BoundingBox(west = 10.0, south = 50.0, east = 10.0, north = 50.0)
        val camera = fitCamera(bounds, card)

        assertEquals(MAX_FIT_ZOOM, camera.zoom)
        assertEquals(10.0, camera.center.longitude, 1e-9)
        assertEquals(50.0, camera.center.latitude, 1e-9)
    }

    @Test
    fun aRouteNearSixtyNorthFitsBothWays() {
        // At 60°N a degree of latitude is drawn about twice as tall as a degree of longitude is wide.
        val bounds = BoundingBox(west = 25.0, south = 60.0, east = 25.04, north = 60.01)
        val camera = fitCamera(bounds, card)

        val (width, height) = drawnSize(bounds, camera)
        assertTrue(width <= room.first + 1e-6 && height <= room.second + 1e-6, "$width × $height dp")
        assertTrue(width > room.first - 1e-6 || height > room.second - 1e-6, "$width × $height dp fills neither side")
        // 0.04° by 0.01° is drawn about 2:1 here; at the equator it would be 4:1.
        assertEquals(0.5, height / width, 0.01)
        assertTrue(camera.center.latitude in 60.0..60.01, "${camera.center.latitude}")
    }
}
