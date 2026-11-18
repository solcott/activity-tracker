package io.github.solcott.activitytracker.data.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NearestIndexTest {
    private val xs = listOf(0.0, 4.0, 8.0, 10.0)

    private fun nearest(x: Double) = xs.nearestIndexBy(x) { it }

    @Test
    fun theNearestItemIsTheClosestByX() {
        assertEquals(1, nearest(5.0))
        assertEquals(2, nearest(7.0))
        assertEquals(1, nearest(4.0), "an exact match")
    }

    @Test
    fun aTieGoesToTheEarlierItem() {
        assertEquals(0, nearest(2.0))
    }

    @Test
    fun beforeTheStartOrPastTheEndIsTheEndItem() {
        assertEquals(0, nearest(-3.0))
        assertEquals(3, nearest(12.0))
        assertEquals(0, listOf(5.0).nearestIndexBy(9.0) { it })
    }

    @Test
    fun anEmptyListHasNone() {
        assertFailsWith<IllegalArgumentException> { emptyList<Double>().nearestIndexBy(1.0) { it } }
    }
}
