package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.media.mediaItems
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_card_counts
import io.github.solcott.activitytracker.composeApp.resources.comments_count
import io.github.solcott.activitytracker.composeApp.resources.like_content_description
import io.github.solcott.activitytracker.composeApp.resources.like_count
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityCard(
    activity: ActivityView,
    onClick: () -> Unit,
    /** Null leaves the author unclickable, e.g. on their own profile. */
    onAuthorClick: (() -> Unit)?,
    /** Null hides the like button, e.g. on the viewer's own activities. A non-public activity never shows it, nor any counts. */
    onLike: (() -> Unit)?,
    /** Opens the activity's media full screen, at an index. */
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The viewer's own activity: each detail category stored narrower than it shows a lock. */
    isOwn: Boolean = false,
    /** The viewer revealed its blurred photos on this screen. */
    revealed: Boolean = false,
    onReveal: () -> Unit = {},
) {
    // Every detail the viewer was served, with what they weren't left out.
    val record = remember(activity) { activity.effectiveRecord() }
    val decision = remember(activity, isOwn) { ModerationDecision.of(activity.labels, activity.author.labels, isOwn) }
    val blur = remember(activity) { ModerationDecision.mediaOf(activity.labels, activity.author.labels) }
    Card(modifier.fillMaxWidth().clickable(onClick = onClick).handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val author = activity.author.displayName ?: activity.author.handle.value
            Row(
                if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick).handCursor() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Revealed on the athlete's profile: a tap here opens it.
                Avatar(activity.author.avatar, author, 32.dp, blur = ModerationDecision.mediaOf(null, activity.author.labels))
                Text(author, style = MaterialTheme.typography.labelLarge)
                VisibilityBadge(activity.visibilityOrPublic)
            }
            ModerationNotice(decision, isOwn, warn = ModerationDecision.warnOf(activity.labels, activity.author.labels))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val typeLabel = activityTypeLabel(record.activityType)
                // Untitled cards already show the type as their title, so the icon only needs describing when there's a title.
                ActivityTypeIcon(record.activityType, contentDescription = typeLabel.takeIf { record.title != null })
                Text(record.title ?: typeLabel, style = MaterialTheme.typography.titleMedium)
            }
            record.dive?.site?.let { DiveSite(it) }
            record.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            StatsRow(
                if (record.sport == Sport.DIVE) diveCardStats(record)
                else
                    listOfNotNull(
                            distanceStat(record.sport, record.distance?.toDouble()),
                            Stat(stringResource(Res.string.stat_time), Format.duration(record.movingTime ?: record.elapsedTime)),
                            // Pace follows the Pace/Speed category: a viewer not served the speed isn't shown it.
                            record.avgSpeed?.let {
                                Format.rateStat(record.sport, record.distance?.toDouble(), record.movingTime ?: record.elapsedTime)
                            },
                        )
                        .toImmutableList()
            )
            if (isOwn) CategoryLocks(remember(activity) { narrowerCategories(activity) })
            val gaps = remember(record) { record.polylineGaps?.toImmutableList() }
            MediaRow(
                remember(activity) { activity.mediaItems() },
                onOpen = { onOpenMedia(it) },
                height = 160.dp,
                map =
                    record.polyline?.let { polyline -> { mapModifier, mapHeight -> RoutePreview(polyline, mapModifier, mapHeight, gaps) } },
                blur = blur,
                revealed = revealed,
                onReveal = onReveal,
                takenDown = decision == ModerationDecision.Takedown,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onLike != null) {
                    val given = activity.viewer?.like != null
                    AppIconButton(
                        AppIcon.Like,
                        contentDescription = stringResource(Res.string.like_content_description),
                        onClick = onLike,
                        filled = given,
                        tint = if (given) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    )
                }
                Text(
                    stringResource(
                        Res.string.activity_card_counts,
                        pluralStringResource(Res.plurals.like_count, activity.likeCount.toInt(), formatWhole(activity.likeCount)),
                        pluralStringResource(Res.plurals.comments_count, activity.commentCount.toInt(), formatWhole(activity.commentCount)),
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

/** A dive's site, by name, with a location pin. */
@Composable
fun DiveSite(name: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PlatformIcon(AppIcon.Location, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
