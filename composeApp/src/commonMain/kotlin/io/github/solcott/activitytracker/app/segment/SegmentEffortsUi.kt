package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.settings.HistoryDate
import io.github.solcott.activitytracker.app.settings.longDate
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_ebike
import io.github.solcott.activitytracker.composeApp.resources.segment_not_ranked_excluded
import io.github.solcott.activitytracker.composeApp.resources.segment_not_ranked_hidden
import io.github.solcott.activitytracker.composeApp.resources.segment_not_ranked_implausible
import io.github.solcott.activitytracker.composeApp.resources.segment_pr
import io.github.solcott.activitytracker.composeApp.resources.segment_rank_ordinals
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_hike
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_paddle
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_ride
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_run
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_ski
import io.github.solcott.activitytracker.composeApp.resources.segment_status_archived
import io.github.solcott.activitytracker.composeApp.resources.segment_status_duplicate
import io.github.solcott.activitytracker.composeApp.resources.segment_status_proposed
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * One effort as a row: [label] (the segment's name on an activity page, the date on a segment page), with its time, marked "PR" when it's
 * the athlete's fastest the viewer may see and with its rank when it's in the top 10 of the viewer's leaderboard, and with [showStatus] its
 * segment's status when it isn't active. The athlete's own effort that leaderboards don't rank says why. Tapping it calls [onClick].
 */
@Composable
fun EffortRow(effort: EffortView, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, showStatus: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (showStatus)
                segmentStatusLabel(effort.segment.status)?.let { status ->
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            notRankedLabel(effort.notRanked)?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (effort.eBike == true) {
                Text(
                    stringResource(Res.string.segment_ebike),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        effort.rank?.let { rank ->
            stringArrayResource(Res.array.segment_rank_ordinals).getOrNull(rank - 1)?.let { ordinal ->
                Text(
                    ordinal,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (effort.best == true) {
            Text(
                stringResource(Res.string.segment_pr),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(Format.duration(effort.elapsed), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** Why leaderboards don't rank an effort ([NotRanked]), as a line; null when they do, or for a reason this app doesn't know. */
@Composable
private fun notRankedLabel(reason: String?): String? =
    when (reason) {
        NotRanked.HIDDEN -> stringResource(Res.string.segment_not_ranked_hidden)
        NotRanked.EXCLUDED -> stringResource(Res.string.segment_not_ranked_excluded)
        NotRanked.IMPLAUSIBLE -> stringResource(Res.string.segment_not_ranked_implausible)
        else -> null
    }

/** An effort's `YYYY-MM-DD` date, written out; the string itself when it doesn't parse. */
@Composable
fun effortDateLabel(startedOn: String): String {
    val parts = startedOn.split('-').mapNotNull { it.toIntOrNull() }
    return if (parts.size == 3) longDate(HistoryDate(parts[0], parts[1], parts[2])) else startedOn
}

/** A segment's [status] as a label; null for an active one, or one with no status. */
@Composable
fun segmentStatusLabel(status: String?): String? =
    when (SegmentStatus.of(status)) {
        SegmentStatus.PROPOSED -> stringResource(Res.string.segment_status_proposed)
        SegmentStatus.DUPLICATE -> stringResource(Res.string.segment_status_duplicate)
        SegmentStatus.ARCHIVED -> stringResource(Res.string.segment_status_archived)
        else -> null
    }

/** A segment's sport group as a label; the token itself for one this app doesn't know. */
@Composable
fun segmentSportLabel(sport: String): String =
    when (sport) {
        SegmentSportGroup.RIDE -> stringResource(Res.string.segment_sport_ride)
        SegmentSportGroup.RUN -> stringResource(Res.string.segment_sport_run)
        SegmentSportGroup.HIKE -> stringResource(Res.string.segment_sport_hike)
        SegmentSportGroup.SKI -> stringResource(Res.string.segment_sport_ski)
        SegmentSportGroup.PADDLE -> stringResource(Res.string.segment_sport_paddle)
        else -> sport
    }
