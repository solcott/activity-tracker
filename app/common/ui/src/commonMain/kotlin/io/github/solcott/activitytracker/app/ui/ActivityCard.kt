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
import io.github.solcott.activitytracker.app.activitylist.ActivityCardContent
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.like_content_description
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    val content = remember(activity, isOwn) { ActivityCardContent(activity, isOwn) }
    val record = content.record
    val units = LocalUnitSystem.current
    Card(modifier.fillMaxWidth().clickable(onClick = onClick).handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // A blurred avatar is revealed on the athlete's profile, which a tap here opens.
            AuthorHeader(activity.author, onClick = onAuthorClick, beside = { VisibilityBadge(activity.visibilityOrPublic) }) {
                Text(
                    content.start(LocalClock.current.now(), LocalTimeZone.current).string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content.together()?.let {
                    Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ModerationNotice(content.decision, isOwn, warn = content.warn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Untitled cards already show the type as their title, so the icon only needs describing when there's a title.
                ActivityTypeIcon(content.type, contentDescription = content.typeDescription()?.string())
                Text(content.title().string(), style = MaterialTheme.typography.titleMedium)
            }
            content.diveSite()?.let { DiveSite(it) }
            content.descriptionText()?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            StatsRow(content.stats(units).stats())
            if (isOwn) CategoryLocks(remember(activity) { narrowerCategories(activity) })
            val gaps = remember(record) { record.polylineGaps?.toImmutableList() }
            MediaRow(
                content.media,
                onOpen = { onOpenMedia(it) },
                height = 160.dp,
                map =
                    record.polyline?.let { polyline -> { mapModifier, mapHeight -> RouteMapTile(polyline, mapHeight, gaps, mapModifier) } },
                blur = content.blur,
                revealed = revealed,
                onReveal = onReveal,
                takenDown = content.takenDown,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onLike != null) {
                    AppIconButton(
                        AppIcon.Like,
                        contentDescription = stringResource(Res.string.like_content_description),
                        onClick = onLike,
                        filled = content.liked,
                        tint = if (content.liked) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    )
                }
                Text(content.counts().string(), style = MaterialTheme.typography.bodySmall)
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

/** Whom an activity was done with (feed/group-activities): "with Bob", "with Bob and Carol", or "with Bob and 2 others". */
@Composable fun togetherWith(partners: ImmutableList<GroupPartner>): String = ActivityCardContent.togetherWith(partners)?.string().orEmpty()
