package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevationProfileTest {
    @Test
    fun straightClimbComesBackUnchanged() {
        val heights = List(50) { 100.0 + it * 1.5 }
        ElevationProfile.smooth(heights, spacingM = 10.0).zip(heights).forEach { (smoothed, raw) -> assertEquals(raw, smoothed, 1e-9) }
    }

    @Test
    fun endsKeepTheirHeights() {
        val heights = listOf(10.0, 30.0, 0.0, 50.0, 20.0, 40.0, 5.0)
        val smoothed = ElevationProfile.smooth(heights, spacingM = 10.0)
        assertEquals(10.0, smoothed.first())
        assertEquals(5.0, smoothed.last())
        // The second sample has one neighbour each side, the middle ones two.
        assertEquals((10.0 + 30.0 + 0.0) / 3, smoothed[1], 1e-9)
        assertEquals((10.0 + 30.0 + 0.0 + 50.0 + 20.0) / 5, smoothed[2], 1e-9)
    }

    @Test
    fun shortRipplesOnAClimbDontCount() {
        // A steady 100 m climb whose terrain also rises and falls by 4 m every 20 m.
        val heights = List(101) { it.toDouble() + if (it % 2 == 0) 4.0 else 0.0 }
        assertTrue(ElevationGain.of(heights)!!.loss > 0, "raw ripples count as climbing")
        assertEquals(GainLoss(100, 0), ElevationGain.of(ElevationProfile.smooth(heights, spacingM = 10.0)))
    }

    @Test
    fun noisyFlatStaysFlat() {
        val heights = List(200) { 100.0 + if (it % 2 == 0) 1.0 else -1.0 }
        assertEquals(GainLoss(0, 0), ElevationGain.of(ElevationProfile.smooth(heights, spacingM = 10.0)))
    }

    @Test
    fun evenRunsFromStartToEndInDecimeters() {
        // 10 m samples with a shorter last stretch, as resampling leaves it.
        val atM = listOf(0.0, 10.0, 20.0, 25.0)
        val heights = listOf(100.0, 102.0, 104.0, 110.0)
        val profile = ElevationProfile.even(heights, atM, count = 6)
        assertEquals(6, profile.size)
        assertEquals(1000, profile.first())
        assertEquals(1100, profile.last())
        // 5 m along: halfway between the first two heights.
        assertEquals(1010, profile[1])
        // 15 m along: halfway between the second and third.
        assertEquals(1030, profile[3])
    }

    @Test
    fun evenDefaultsToTheProfileSize() {
        val atM = List(300) { it * 10.0 }
        val profile = ElevationProfile.even(atM.map { it / 10 }, atM)
        assertEquals(ElevationProfile.PROFILE_POINTS, profile.size)
        assertTrue(profile.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun evenFromTwoHeights() {
        assertEquals(listOf(0, 50, 100), ElevationProfile.even(listOf(0.0, 10.0), listOf(0.0, 200.0), count = 3))
        assertEquals(emptyList(), ElevationProfile.even(listOf(5.0), listOf(0.0)))
    }
}
