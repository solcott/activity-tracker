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
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
            Text(
                activity.author.displayName ?: activity.author.handle.value,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onAuthorClick),
            )
            Text(
                record.title ?: record.sport.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
            )
            StatsRow(
                persistentListOf(
                    Stat("Distance", Format.distance(record.distance?.toDouble())),
                    Stat("Time", Format.duration(record.movingTime ?: record.elapsedTime)),
                    Stat("Pace", Format.pace(record.distance?.toDouble(), record.movingTime ?: record.elapsedTime)),
                )
            )
            record.polyline?.let { RoutePreview(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onKudos != null) {
                    IconButton(onClick = onKudos) {
                        Icon(
                            if (activity.viewer?.kudos != null) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Kudos",
                        )
                    }
                }
                Text(
                    "${activity.kudosCount} kudos · ${activity.commentCount} comments",
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
