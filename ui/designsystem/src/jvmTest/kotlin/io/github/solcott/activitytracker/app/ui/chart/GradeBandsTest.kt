package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradeBandsTest {
    @Test
    fun aStopWhereverTheBandChangesHalfwayBetweenPoints() {
        val at = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        assertEquals(
            listOf(BandStop(0f, 0), BandStop(0.375f, 2), BandStop(0.875f, 4)),
            GradeBands.stops(at, listOf(0, 0, 2, 2, 4), minPoints = 1),
        )
    }

    @Test
    fun aGradeHoveringAtABandsEdgeDoesntFlicker() {
        val at = List(12) { it / 11f }
        // Alternating 0 and 1 points stay 0; the steady run of 3s later is kept.
        val bands = listOf(0, 1, 0, 1, 0, 1, 0, 3, 3, 3, 3, 3)
        assertEquals(listOf(BandStop(0f, 0), BandStop((at[6] + at[7]) / 2, 3)), GradeBands.stops(at, bands))
    }

    @Test
    fun oneBandIsOneStop() {
        assertEquals(listOf(BandStop(0f, 1)), GradeBands.stops(listOf(0f, 0.5f, 1f), listOf(1, 1, 1)))
        assertEquals(emptyList(), GradeBands.stops(emptyList(), emptyList()))
    }

    @Test
    fun bandsStandOutFromTheirSurface() {
        // The light set against a white surface, the dark set against a near-black one: at least 2:1 each, as a chart fill needs.
        GradeBands.ON_LIGHT.forEach { assertTrue(contrast(it, Color.White) >= 2.0, "$it on white: ${contrast(it, Color.White)}") }
        GradeBands.ON_DARK.forEach { assertTrue(contrast(it, Color(0xFF121212)) >= 2.0, "$it on dark: ${contrast(it, Color(0xFF121212))}") }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb) / min(la, lb)
    }
}
