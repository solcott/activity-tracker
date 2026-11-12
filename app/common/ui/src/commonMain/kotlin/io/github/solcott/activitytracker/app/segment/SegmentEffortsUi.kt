package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allDrawableResources
import io.github.solcott.activitytracker.lexicon.app.EffortView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * One effort as a row: [label] (the segment's name on an activity page, the date on a segment page), with its time, marked "PR" when it's
 * the athlete's fastest the viewer may see and with its rank when it's in the top 10 of the viewer's leaderboard, and with [showStatus] its
 * segment's status when it isn't active, and with [showKind] its [SegmentKindIcon]. The athlete's own effort that leaderboards don't rank
 * says why. Tapping it calls [onClick].
 */
@Composable
@Suppress("LongParameterList") // The effort, its label and click, then what the row shows beside it.
fun EffortRow(
    effort: EffortView,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStatus: Boolean = false,
    showKind: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showKind) SegmentKindIcon(effort.segment.sport, effort.segment.kind)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val notes =
                listOfNotNull(EffortTexts.status(effort).takeIf { showStatus }, EffortTexts.notRanked(effort), EffortTexts.eBike(effort))
            notes.forEach { note ->
                Text(note.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EffortTexts.rank(effort)?.let { ordinal ->
            Text(
                ordinal.string(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        EffortTexts.pr(effort)?.let { pr ->
            Text(
                pr.string(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(EffortTexts.time(effort), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** An effort's `YYYY-MM-DD` date, written out; the string itself when it doesn't parse. `DateText` chooses it. */
@Composable fun effortDateLabel(startedOn: String): String = DateText.isoDate(startedOn).string()

/** A segment's [status] as a label; null for an active one, or one with no status. */
@Composable fun segmentStatusLabel(status: String?): String? = PlaceTexts.status(status)?.string()

/**
 * What a segment of [sport] and [kind] is, as a label: a ski lift or a ski run for `alpine` ones (segments/segment-lifts), else its sport
 * group ([segmentSportLabel]).
 */
@Composable fun segmentTypeLabel(sport: String, kind: String?): String = SegmentTexts.type(sport, kind).string()

/** The icon that tells a ski lift from a ski run ([SegmentTexts.kindIcon]); null for every other segment, which needs none. */
fun segmentKindIcon(sport: String, kind: String?): DrawableResource? =
    SegmentTexts.kindIcon(sport, kind)?.let { Res.allDrawableResources[it] }

/** [segmentKindIcon] for a segment of [sport] and [kind], read out as its [segmentTypeLabel]; nothing for a segment with none. */
@Composable
fun SegmentKindIcon(sport: String, kind: String?, modifier: Modifier = Modifier) {
    val icon = segmentKindIcon(sport, kind) ?: return
    Icon(
        painterResource(icon),
        contentDescription = segmentTypeLabel(sport, kind),
        modifier = modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A segment's sport group as a label; the token itself for one this app doesn't know. */
@Composable fun segmentSportLabel(sport: String): String = SegmentTexts.sportGroup(sport).string()
