package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class GradeBandsTest {
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
