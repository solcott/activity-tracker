package io.github.solcott.activitytracker.app.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The colours a map draws a run in by its rating level (segments/trail-ratings), easiest to hardest, as trail signs colour them: white,
 * green, blue, black and black again, which the map draws as a double line. Each level has a casing in a colour that stands out from it, so
 * white and black lines read on light and dark maps alike. The iOS app's catalog colours `TrailRating0Color`… hold the same values.
 */
@Immutable
class TrailRatingColors
internal constructor(
    /** A line per level, Easiest first. */
    val lines: ImmutableList<Color>,
    /** The casing under each level's line. */
    val casings: ImmutableList<Color>,
    /** An unrated run's line, on a white casing. */
    val unrated: Color,
) {
    /** The line of level [index] (0 Easiest to 4 Extreme), or [unrated] for null. */
    fun line(index: Int?): Color = index?.let { lines[it.coerceIn(0, lines.lastIndex)] } ?: unrated

    /** The casing under level [index], or under an unrated run for null. */
    fun casing(index: Int?): Color = index?.let { casings[it.coerceIn(0, casings.lastIndex)] } ?: CASING

    companion object {
        /** The palette for the current theme's surface: green and blue a step lighter on a dark one. */
        @Composable
        @ReadOnlyComposable
        fun current(): TrailRatingColors = if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE) ON_DARK else ON_LIGHT

        /** The thin line down the middle of an Extreme run, which makes its black line a double line. */
        val EXTREME_CENTRE: Color = Color.White

        private const val DARK_SURFACE = 0.5f
        private val CASING = Color.White
        private val WHITE = Color.White
        private val BLACK = Color(0xFF111111)
        private val DARK_CASING = Color(0xFF424242)
        private val CASINGS = persistentListOf(DARK_CASING, CASING, CASING, CASING, CASING)

        val ON_LIGHT: TrailRatingColors =
            TrailRatingColors(
                persistentListOf(WHITE, Color(0xFF2E7D32), Color(0xFF1565C0), BLACK, BLACK),
                CASINGS,
                unrated = Color(0xFF757575),
            )

        val ON_DARK: TrailRatingColors =
            TrailRatingColors(
                persistentListOf(WHITE, Color(0xFF43A047), Color(0xFF1E88E5), BLACK, BLACK),
                CASINGS,
                unrated = Color(0xFF8A8A8A),
            )
    }
}
