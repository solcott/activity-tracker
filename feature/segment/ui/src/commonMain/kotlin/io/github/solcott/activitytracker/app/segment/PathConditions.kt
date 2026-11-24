package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.path_photos_title
import io.github.solcott.activitytracker.composeApp.resources.trail_conditions_title
import io.github.solcott.activitytracker.lexicon.AtUri
import org.jetbrains.compose.resources.stringResource

/**
 * The Trail page's Conditions card (segments/trail-reports): how many of its segments have reports, each of them (closed ones first) with
 * its lead tags and riders, opening that segment, then the trail's newest photos with the total.
 */
@Composable
internal fun PathConditions(card: PathConditionsCard, send: (PathEvent) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().testTag(PATH_CONDITIONS_TAG)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.trail_conditions_title), style = MaterialTheme.typography.titleMedium)
                card.summary?.let {
                    Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            card.segments.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { send(PathEvent.Open(AtUri(row.uri))) }
                        .handCursor()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall)
                        row.riders?.let {
                            Text(
                                it.string(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    row.lead?.let { ConditionChip(it, closed = row.closed) }
                }
            }
            if (card.photos.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.path_photos_title), style = MaterialTheme.typography.titleSmall)
                    Column {
                        PhotoStrip(
                            card.photos,
                            card.morePhotos,
                            onOpen = { send(PathEvent.OpenPhoto(it)) },
                            count = PathConditionsTexts.STRIP,
                        )
                        card.seeAll?.let { AppTextButton(onClick = { send(PathEvent.OpenPhoto(0)) }) { Text(it.string()) } }
                    }
                }
            }
        }
    }
}

/** Where a test finds the Trail page's Conditions card. */
internal const val PATH_CONDITIONS_TAG = "path-conditions"
