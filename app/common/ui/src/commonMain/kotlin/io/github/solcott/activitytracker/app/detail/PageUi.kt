package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import kotlinx.collections.immutable.ImmutableList

/**
 * The activity's headline figures: each value large with its unit smaller, under its label, which wraps rather than being cut off. Two to a
 * row in a narrow column, three where they have room.
 */
@Composable
fun HeroRow(figures: ImmutableList<Figure>, modifier: Modifier = Modifier) {
    if (figures.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < HERO_THREE_COLUMNS) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            figures.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    row.forEach { FigureBlock(it, MaterialTheme.typography.headlineSmall, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A label above its value, the value in [style] with its unit smaller. */
@Composable
internal fun FigureBlock(figure: Figure, style: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(figureLabel(figure), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valueText(figure.value, style), style = style, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun valueText(value: FigureValue, style: TextStyle) =
    withSmallUnits(figureText(value), SpanStyle(fontSize = style.fontSize * UNIT_SCALE, fontWeight = FontWeight.Normal))

/**
 * A label and its value on one line: the label takes the free width and wraps, the value sits at the end in full. A figure with a [mark] (a
 * category narrower than the activity, on the owner's page) shows its visibility after the label.
 */
@Composable
internal fun FigureRow(figure: Figure, modifier: Modifier = Modifier) {
    LabelValueRow(figureLabel(figure), figureText(figure.value), modifier, figure.mark)
}

@Composable
internal fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier, mark: String? = null) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
            mark?.let { VisibilityBadge(it) }
        }
        // Measured before the label, so it keeps its width; capped so a long one (a sensor's name) wraps rather than crowd the label out.
        Text(
            value,
            Modifier.widthIn(max = VALUE_MAX_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

/** A card's title, marked with its category's visibility on the owner's page when it's narrower than the activity. */
@Composable
internal fun CardTitle(title: String, mark: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
        mark?.let { VisibilityBadge(it) }
    }
}

private val HERO_THREE_COLUMNS = 340.dp
internal val ROW_GAP = 12.dp
private val VALUE_MAX_WIDTH = 220.dp
private const val UNIT_SCALE = 0.6f
