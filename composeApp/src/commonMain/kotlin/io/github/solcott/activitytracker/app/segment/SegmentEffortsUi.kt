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
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_ebike
import io.github.solcott.activitytracker.composeApp.resources.segment_pr
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_hike
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_paddle
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_ride
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_run
import io.github.solcott.activitytracker.composeApp.resources.segment_sport_ski
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import org.jetbrains.compose.resources.stringResource

/**
 * One effort as a row: [label] (the segment's name on an activity page, the date on a segment page), with its time, marked "PR" when it's
 * the athlete's fastest the viewer may see. Tapping it calls [onClick].
 */
@Composable
fun EffortRow(effort: EffortView, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (effort.eBike == true) {
                Text(
                    stringResource(Res.string.segment_ebike),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
