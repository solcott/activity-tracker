package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentCellsTest {
    @Test
    fun packsLatitudeAndLongitudeCells() {
        assertEquals(4_700L * 100_000 + 800, SegmentCells.cellOf(47.005, 8.005))
        assertEquals(-3_400L * 100_000 - 15_100, SegmentCells.cellOf(-33.995, -150.995))
    }

    @Test
    fun pointNearAnEdgeIncludesTheNeighbour() {
        // 47.00999° is about 1 m below the 47.01° edge.
        val cells = SegmentCells.cellsOf(GeoPoint(47.00999, 8.005))
        assertEquals(setOf(SegmentCells.cellOf(47.005, 8.005), SegmentCells.cellOf(47.015, 8.005)), cells)
        assertEquals(setOf(SegmentCells.cellOf(47.005, 8.005)), SegmentCells.cellsOf(GeoPoint(47.005, 8.005)))
    }

    @Test
    fun lineCrossingABoundaryCoversBothCells() {
        // Two points 300 m apart, each well inside its own cell, either side of the 8.01° meridian.
        val cells = SegmentCells.cellsOf(listOf(GeoPoint(47.005, 8.008), GeoPoint(47.005, 8.012)))
        assertTrue(SegmentCells.cellOf(47.005, 8.005) in cells)
        assertTrue(SegmentCells.cellOf(47.005, 8.015) in cells)
        assertEquals(2, cells.size)
    }

    @Test
    fun boxCoversItsCells() {
        assertEquals(4, SegmentCells.cellsIn(47.005, 8.005, 47.015, 8.015).size)
    }
}
