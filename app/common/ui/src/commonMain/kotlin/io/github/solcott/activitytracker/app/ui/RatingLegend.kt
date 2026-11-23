package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RatingLegendEntry
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.map.TrailRatingColors
import kotlinx.collections.immutable.ImmutableList

/**
 * A short sample of how a map draws [rating]'s line (segments/trail-ratings): the level's colour on its casing, Extreme as a double line,
 * an unrated run grey. A screen reader reads the level's name; nothing is drawn for null unless [showUnrated].
 */
@Composable
fun RatingSwatch(rating: TrailRating?, modifier: Modifier = Modifier, showUnrated: Boolean = false) {
    if (rating == null && !showUnrated) return
    val colors = TrailRatingColors.current()
    val label = (rating?.label ?: TrailRating.unratedLabel).string()
    Canvas(modifier.size(SWATCH_LENGTH, SWATCH_HEIGHT).testTag(TestTags.TRAIL_RATING_SWATCH).semantics { contentDescription = label }) {
        ratedLine(rating, colors, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
    }
}

/** A line from [start] to [end] as a map draws [rating]'s run: casing, line, and for Extreme a thin centre line. */
internal fun DrawScope.ratedLine(rating: TrailRating?, colors: TrailRatingColors, start: Offset, end: Offset, width: Dp = LINE_WIDTH) {
    val level = rating?.ordinal
    drawLine(colors.casing(level), start, end, strokeWidth = (width + CASING_EXTRA).toPx(), cap = StrokeCap.Round)
    drawLine(colors.line(level), start, end, strokeWidth = width.toPx(), cap = StrokeCap.Round)
    if (rating == TrailRating.EXTREME) {
        drawLine(TrailRatingColors.EXTREME_CENTRE, start, end, strokeWidth = (width / EXTREME_CENTRE_SHARE).toPx(), cap = StrokeCap.Round)
    }
}

/**
 * A map's legend (segments/trail-ratings): each level its lines are drawn in, with its swatch and name, Easiest first and Unrated last, on
 * a small card. Nothing when [entries] is empty, as [io.github.solcott.activitytracker.app.SegmentsMap.legend] is with no rated run.
 */
@Composable
fun RatingLegend(entries: ImmutableList<RatingLegendEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val title = AppText.named("trail_rating_legend").string()
    Surface(
        modifier.testTag(TestTags.TRAIL_RATING_LEGEND).semantics { contentDescription = title },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_ALPHA),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (entry in entries) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RatingSwatch(entry.rating, showUnrated = true)
                    Text(entry.label.string(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val SWATCH_LENGTH = 18.dp
private val SWATCH_HEIGHT = 8.dp
internal val LINE_WIDTH = 3.dp

/** How much wider than its line a run's casing is, both sides together. */
internal val CASING_EXTRA = 1.dp

/** An Extreme run's centre line is this share of its line's width. */
private const val EXTREME_CENTRE_SHARE = 3f
private const val LEGEND_ALPHA = 0.92f
