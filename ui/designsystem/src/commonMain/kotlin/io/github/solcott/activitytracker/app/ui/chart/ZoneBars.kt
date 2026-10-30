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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * Time in each zone as horizontal bars, highest zone on top. Each row reads as one sentence to a screen reader. Zones are coloured from
 * cool to hot.
 */
@Composable
fun ZoneBars(zones: ImmutableList<ZoneBar>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        zones.asReversed().forEachIndexed { reversed, zone ->
            val color = zoneColor(zones.size - 1 - reversed, zones.size)
            // Rounded, and never 0% beside a sliver of bar.
            val rounded = (zone.fraction * PERCENT).roundToInt()
            val percent = if (rounded == 0 && zone.fraction > 0f) "<1%" else "$rounded%"
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "${zone.label} ${zone.name}, ${zone.range}: ${zone.time}, $percent"
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.width(LABEL_WIDTH.dp)) {
                    Text("${zone.label} ${zone.name}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(zone.range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.weight(1f).height(14.dp)) {
                    // No time in the zone, no bar; any time at all still shows a sliver.
                    if (zone.fraction > 0f)
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(zone.fraction.coerceIn(MIN_BAR, 1f))
                                .background(color, RoundedCornerShape(3.dp))
                        )
                }
                Text(zone.time, Modifier.width(TIME_WIDTH.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text(
                    percent,
                    Modifier.width(PERCENT_WIDTH.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** The lowest zone is grey; the rest run blue, green, yellow, orange, red and purple, dropping the middle colours when there are fewer. */
private fun zoneColor(index: Int, count: Int): Color {
    if (index == 0) return RECOVERY
    val hot =
        when (count - 1) {
            in HOT.size..Int.MAX_VALUE -> HOT
            HOT.size - 1 -> HOT.dropLast(1)
            HOT.size - 2 -> listOf(BLUE, GREEN, ORANGE, RED)
            else -> listOf(GREEN, ORANGE, RED)
        }
    return hot[(index - 1).coerceAtMost(hot.lastIndex)]
}

private val RECOVERY = Color(0xFF9E9E9E)
private val BLUE = Color(0xFF42A5F5)
private val GREEN = Color(0xFF66BB6A)
private val YELLOW = Color(0xFFFFCA28)
private val ORANGE = Color(0xFFFFA726)
private val RED = Color(0xFFEF5350)
private val PURPLE = Color(0xFFAB47BC)
private val HOT = listOf(BLUE, GREEN, YELLOW, ORANGE, RED, PURPLE)

private const val PERCENT = 100
private const val MIN_BAR = 0.005f
private const val LABEL_WIDTH = 132
private const val TIME_WIDTH = 56
private const val PERCENT_WIDTH = 40
