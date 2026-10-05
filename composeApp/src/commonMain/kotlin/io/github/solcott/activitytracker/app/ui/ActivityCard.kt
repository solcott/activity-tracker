package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_card_counts
import io.github.solcott.activitytracker.composeApp.resources.comments_count
import io.github.solcott.activitytracker.composeApp.resources.kudos_content_description
import io.github.solcott.activitytracker.composeApp.resources.kudos_count
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityCard(
    activity: ActivityView,
    onClick: () -> Unit,
    onAuthorClick: () -> Unit,
    /** Null hides the kudos button, e.g. on the viewer's own activities. */
    onKudos: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val record = activity.record
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val author = activity.author.displayName ?: activity.author.handle.value
            Row(
                Modifier.clickable(onClick = onAuthorClick),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(activity.author.avatar, author, 32.dp)
                Text(author, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                record.title ?: activityTypeLabel(record.activityType),
                style = MaterialTheme.typography.titleMedium,
            )
            StatsRow(
                listOfNotNull(
                        Stat(stringResource(Res.string.stat_distance), Format.distance(record.distance?.toDouble())),
                        Stat(stringResource(Res.string.stat_time), Format.duration(record.movingTime ?: record.elapsedTime)),
                        Format.rateStat(record.sport, record.distance?.toDouble(), record.movingTime ?: record.elapsedTime),
                    )
                    .toImmutableList()
            )
            record.polyline?.let { RoutePreview(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onKudos != null) {
                    IconButton(onClick = onKudos) {
                        Icon(
                            if (activity.viewer?.kudos != null) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(Res.string.kudos_content_description),
                        )
                    }
                }
                Text(
                    stringResource(
                        Res.string.activity_card_counts,
                        pluralStringResource(Res.plurals.kudos_count, activity.kudosCount.toInt(), activity.kudosCount),
                        pluralStringResource(Res.plurals.comments_count, activity.commentCount.toInt(), activity.commentCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** One labelled figure in a [StatsRow]. */
@Immutable data class Stat(val label: String, val value: String)

@Composable
fun StatsRow(stats: ImmutableList<Stat>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        stats.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
