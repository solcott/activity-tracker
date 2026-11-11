package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

class RouteMarkersTest {
    @Test
    fun anUnbrokenLineHasOnlyAStartAndEnd() {
        assertEquals(RouteMarkers(0, 4, persistentListOf()), RouteMarkers.of(listOf(listOf(0, 1, 2, 3, 4))))
    }

    @Test
    fun eachBreakMarksBothItsEnds() {
        assertEquals(
            RouteMarkers(0, 8, persistentListOf(RouteMarkers.Break(2, 3), RouteMarkers.Break(5, 6))),
            RouteMarkers.of(listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8))),
        )
    }

    @Test
    fun aOnePointPieceIsBothEndsOfItsBreaks() {
        assertEquals(
            RouteMarkers(0, 3, persistentListOf(RouteMarkers.Break(1, 2), RouteMarkers.Break(2, 3))),
            RouteMarkers.of(listOf(listOf(0, 1), listOf(2), listOf(3))),
        )
    }

    @Test
    fun noPointsHaveNoMarkers() {
        assertNull(RouteMarkers.of(emptyList<List<Int>>()))
        assertNull(RouteMarkers.of(listOf(emptyList<Int>())))
    }

    @Test
    fun givenEndsReplaceTheLinesOwn() {
        val line = RouteMarkers.of(listOf(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), listOf(GeoPoint(0.0, 2.0), GeoPoint(0.0, 3.0))))!!
        val ends = RouteEnds(GeoPoint(0.0, 0.2), GeoPoint(0.0, 2.8))
        assertEquals(RouteMarkers(GeoPoint(0.0, 0.2), GeoPoint(0.0, 2.8), line.breaks), line.shownAt(ends))
        assertEquals(RouteMarkers(null, null, line.breaks), line.shownAt(RouteEnds.HIDDEN))
        assertEquals(line, line.shownAt(null))
    }
}
