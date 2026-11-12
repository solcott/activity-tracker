package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RouteMapTest {
    private val line = (0..8).map { GeoPoint(0.0, it * 0.001) }
    private val polyline = encode(line)

    private fun encode(points: List<GeoPoint>): String =
        TrackMath.encodePolyline(points.map { LocationSample(it.lat, it.lng, null, null, null, Instant.fromEpochSeconds(0)) })

    @Test
    fun anUnbrokenLineHasOnlyAStartAndEnd() {
        val map = assertNotNull(RouteMap.of(polyline))
        assertEquals(1, map.pieces.size)
        assertEquals(line.first(), map.start)
        assertEquals(line.last(), map.end)
        assertTrue(map.breaks.isEmpty())
    }

    @Test
    fun eachBreakMarksBothItsEnds() {
        val map = assertNotNull(RouteMap.of(polyline, gaps = listOf(3, 6)))
        assertEquals(3, map.pieces.size)
        assertEquals(listOf(MapBreak(line[3], line[4]), MapBreak(line[6], line[7])), map.breaks)
        assertEquals(line.first(), map.start)
        assertEquals(line.last(), map.end)
    }

    @Test
    fun givenEndsReplaceTheLinesOwn() {
        val ends = RouteEnds(GeoPoint(0.0, 0.0002), GeoPoint(0.0, 0.0078))
        val shown = assertNotNull(RouteMap.of(polyline, ends = ends))
        assertEquals(ends.start, shown.start)
        assertEquals(ends.end, shown.end)
        val hidden = assertNotNull(RouteMap.of(polyline, ends = RouteEnds.HIDDEN))
        assertNull(hidden.start)
        assertNull(hidden.end)
    }

    @Test
    fun underTwoPointsIsNoMap() {
        assertNull(RouteMap.of(encode(listOf(GeoPoint(1.0, 1.0)))))
        assertNull(RouteMap.of("not a polyline"))
    }

    @Test
    fun boundsAreTheStoredPoints() {
        val map = assertNotNull(RouteMap.of(polyline))
        assertEquals(MapBounds(west = 0.0, south = 0.0, east = 0.008, north = 0.0), map.bounds)
    }

    @Test
    fun theSketchMarksWhatTheMapMarks() {
        val hidden = assertNotNull(RouteMap.of(polyline, ends = RouteEnds.HIDDEN)?.sketch(200.0, 100.0, 12.0))
        assertNull(hidden.start)
        assertNull(hidden.end)
        val shown = assertNotNull(RouteMap.of(polyline)?.sketch(200.0, 100.0, 12.0))
        assertEquals(12.0, assertNotNull(shown.start).x, 1e-6)
        assertEquals(188.0, assertNotNull(shown.end).x, 1e-6)
    }
}

class MapFrameTest {
    /** The room left for the route in a 400 by 300 map inside the default margin. */
    private val room = 400.0 - 48 to 300.0 - 48

    /** The route's width and height in map units at [frame]'s zoom. */
    private fun drawnSize(bounds: MapBounds, frame: MapFrame): Pair<Double, Double> {
        fun y(lat: Double) = (1 - ln(tan(PI / 4 + lat * PI / 360)) / PI) / 2
        val world = MapFrame.WORLD * 2.0.pow(frame.zoom)
        return (bounds.east - bounds.west) / 360 * world to (y(bounds.south) - y(bounds.north)) * world
    }

    @Test
    fun anEastWestRouteAtTheEquatorFillsTheWidth() {
        val bounds = MapBounds(west = 0.0, south = 0.0, east = 0.01, north = 0.0)
        val frame = MapFrame.fit(bounds, 400.0, 300.0)
        assertEquals(0.005, frame.longitude, 1e-9)
        assertEquals(0.0, frame.latitude, 1e-9)
        assertEquals(14.595, frame.zoom, 1e-3)
        assertEquals(room.first, drawnSize(bounds, frame).first, 1e-6)
    }

    @Test
    fun aTallRouteFillsTheHeight() {
        val bounds = MapBounds(west = -72.6, south = 44.0, east = -72.59, north = 44.5)
        val frame = MapFrame.fit(bounds, 400.0, 300.0)
        assertEquals(room.second, drawnSize(bounds, frame).second, 1e-6)
        assertTrue(frame.latitude in 44.25..44.26, "${frame.latitude}")
    }

    @Test
    fun aSinglePointIsDrawnAtTheClosestZoom() {
        val frame = MapFrame.fit(MapBounds(10.0, 50.0, 10.0, 50.0), 400.0, 300.0)
        assertEquals(MapFrame.MAX_ZOOM, frame.zoom)
        assertEquals(10.0, frame.longitude, 1e-9)
        assertEquals(50.0, frame.latitude, 1e-9)
    }

    @Test
    fun aTopInsetMovesTheRouteBelowIt() {
        val bounds = MapBounds(west = 0.0, south = -0.01, east = 0.01, north = 0.0)
        val plain = MapFrame.fit(bounds, 400.0, 300.0)
        val inset = MapFrame.fit(bounds, 400.0, 300.0, top = 40.0)
        assertTrue(inset.zoom < plain.zoom, "${inset.zoom} vs ${plain.zoom}")
        // The centre moves north, so the route sits lower in the map, below the inset.
        assertTrue(inset.latitude > plain.latitude, "${inset.latitude} vs ${plain.latitude}")
    }
}
