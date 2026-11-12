package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a map of many segments draws and answers (segments/segment-browse, dev/shared-display-text). */
class SegmentsMapTest {
    // Three short east-west lines a few metres apart near Burlington, and a lift crossing them.
    private val south = line(44.47500)
    private val north = line(44.47512)
    private val lift = SegmentGeometry.toPrecision5(listOf(GeoPoint(44.4749, -73.2105), GeoPoint(44.4753, -73.2105)))

    private fun line(lat: Double) = SegmentGeometry.toPrecision5(listOf(GeoPoint(lat, -73.2110), GeoPoint(lat, -73.2100)))

    private val lines =
        listOf(
            MapSegmentLine("at://a/south", south),
            MapSegmentLine("at://a/lift", lift, lift = true),
            MapSegmentLine("at://a/north", north),
            MapSegmentLine("at://a/bad", "x"),
        )

    @Test
    fun liftsDrawFirstAndTheSelectedRunLast() {
        val map = SegmentsMap.of(lines, selected = "at://a/south")
        assertEquals(listOf("at://a/lift", "at://a/north", "at://a/south"), map.drawn.map { it.uri })
        assertEquals(listOf(MapSegmentRole.Lift, MapSegmentRole.Run, MapSegmentRole.Selected), map.drawn.map { it.role })
    }

    @Test
    fun aTapSelectsTheNearestRunInReach() {
        val map = SegmentsMap.of(lines, selected = null)
        // About 4 m north of the south line and 9 m south of the north one.
        assertEquals("at://a/south", map.nearest(44.475036, -73.2103, withinMeters = 15.0))
        assertEquals("at://a/north", map.nearest(44.475095, -73.2103, withinMeters = 15.0))
    }

    @Test
    fun aTapOutOfReachOrOnALiftSelectsNothing() {
        val map = SegmentsMap.of(lines, selected = null)
        assertNull(map.nearest(44.4760, -73.2103, withinMeters = 15.0))
        // On the lift, 20 m or more from either run.
        assertNull(map.nearest(44.47530, -73.2105, withinMeters = 15.0))
    }

    @Test
    fun theStandInPlacesEveryLineInsideItsPadInDrawingOrder() {
        val sketch = SegmentsMap.of(lines, selected = "at://a/north").sketch(200.0, 100.0, pad = 10.0)
        assertEquals(listOf(MapSegmentRole.Lift, MapSegmentRole.Run, MapSegmentRole.Selected), sketch.map { it.role })
        val points = sketch.flatMap { it.points }
        assertTrue(points.all { it.x in 10.0..190.0 && it.y in 10.0..90.0 })
        assertEquals(emptyList(), SegmentsMap.of(emptyList(), null).sketch(200.0, 100.0, 10.0))
    }

    @Test
    fun aViewportIsReportedIn1e7Degrees() {
        assertEquals(
            MapViewport(444_700_000, -732_200_000, 444_800_000, -732_000_000, 444_750_000, -732_100_000),
            MapViewport.ofDegrees(44.47, -73.22, 44.48, -73.20, 44.475, -73.21),
        )
    }
}
