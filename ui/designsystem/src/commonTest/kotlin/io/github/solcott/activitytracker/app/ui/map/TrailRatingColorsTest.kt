package io.github.solcott.activitytracker.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every rating level's line stands out from its casing in both palettes (segments/trail-ratings). */
class TrailRatingColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = max(a.luminance(), b.luminance()) to min(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun everyLineReaches3To1OnItsCasing() {
        for ((name, palette) in listOf("light" to TrailRatingColors.ON_LIGHT, "dark" to TrailRatingColors.ON_DARK)) {
            assertEquals(5, palette.lines.size)
            for (level in listOf(0, 1, 2, 3, 4, null)) {
                val ratio = contrast(palette.line(level), palette.casing(level))
                assertTrue(ratio >= 3f, "$name level $level: $ratio")
            }
        }
    }

    @Test
    fun extremesCentreLineStandsOutFromItsBlack() {
        assertTrue(contrast(TrailRatingColors.ON_LIGHT.line(4), TrailRatingColors.EXTREME_CENTRE) >= 3f)
    }
}
