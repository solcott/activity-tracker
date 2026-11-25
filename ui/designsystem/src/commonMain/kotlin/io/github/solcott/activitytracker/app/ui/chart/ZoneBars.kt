package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * Time in each zone as horizontal bars, highest zone on top, in words shared code chose. Each row reads as one sentence to a screen reader.
 * Zones are coloured from cool to hot.
 */
@Composable
fun ZoneBars(zones: ImmutableList<ZoneBar>, modifier: Modifier = Modifier) {
    val palette = ZonePalette.current()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        zones.asReversed().forEachIndexed { reversed, zone ->
            val color = palette.color(zones.size - 1 - reversed, zones.size)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = zone.spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.width(LABEL_WIDTH.dp)) {
                    Text(zone.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(zone.range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.weight(1f).height(14.dp)) {
                    // No time in the zone, no bar; any time at all still shows a sliver.
                    if (zone.fraction > 0f)
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(zone.fraction.coerceIn(MIN_BAR, 1f))
                                .background(color, RoundedCornerShape(3.dp)),
                        )
                }
                Text(zone.time, Modifier.width(TIME_WIDTH.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text(
                    zone.percent,
                    Modifier.width(PERCENT_WIDTH.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * Time in zones as one thin bar, each zone's share in its colour, lowest first: a strip's glance at a card's zones. Decorative: the card's
 * own bars, once open, say each zone to a screen reader.
 */
@Composable
fun ZoneStrip(fractions: ImmutableList<Float>, modifier: Modifier = Modifier) {
    val palette = ZonePalette.current()
    Row(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).clearAndSetSemantics {}) {
        fractions.forEachIndexed { i, fraction ->
            if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight().background(palette.color(i, fractions.size)))
        }
    }
}

/**
 * Training zones' fixed palette (DESIGN.md "Training zones"): Material's 400 tones on a light surface and its 300 tones on a dark one, as
 * the grade bands step, so each zone stays distinct from the surface in both themes. The iOS app's catalog colours are the same.
 */
private class ZonePalette(private val recovery: Color, blue: Color, green: Color, yellow: Color, orange: Color, red: Color, purple: Color) {
    private val hot = listOf(blue, green, yellow, orange, red, purple)
    private val four = listOf(blue, green, orange, red)
    private val three = listOf(green, orange, red)

    /**
     * The lowest zone is grey; the rest run blue, green, yellow, orange, red and purple, dropping the middle colours when there are fewer.
     */
    fun color(index: Int, count: Int): Color {
        if (index == 0) return recovery
        val colors =
            when (count - 1) {
                in hot.size..Int.MAX_VALUE -> hot
                hot.size - 1 -> hot.dropLast(1)
                hot.size - 2 -> four
                else -> three
            }
        return colors[(index - 1).coerceAtMost(colors.lastIndex)]
    }

    companion object {
        private const val DARK_SURFACE = 0.5f

        private val ON_LIGHT =
            ZonePalette(
                recovery = Color(0xFF9E9E9E),
                blue = Color(0xFF42A5F5),
                green = Color(0xFF66BB6A),
                yellow = Color(0xFFFFCA28),
                orange = Color(0xFFFFA726),
                red = Color(0xFFEF5350),
                purple = Color(0xFFAB47BC),
            )

        private val ON_DARK =
            ZonePalette(
                recovery = Color(0xFFBDBDBD),
                blue = Color(0xFF64B5F6),
                green = Color(0xFF81C784),
                yellow = Color(0xFFFFD54F),
                orange = Color(0xFFFFB74D),
                red = Color(0xFFE57373),
                purple = Color(0xFFBA68C8),
            )

        @Composable
        @ReadOnlyComposable
        fun current(): ZonePalette = if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE) ON_DARK else ON_LIGHT
    }
}

private const val MIN_BAR = 0.005f
private const val LABEL_WIDTH = 132
private const val TIME_WIDTH = 56
private const val PERCENT_WIDTH = 40
