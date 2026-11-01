package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList

/**
 * [activities] as cards keyed by URI, for any screen that lists them inside its own lazy list. Liking is offered on activities not by
 * [viewerDid]. [authorClickable] is false where the author is the page itself, as on a profile. Labeled photos stay blurred until the
 * viewer reveals them on this screen ([revealed]). [gap] goes below each card, for a list that doesn't space its items itself.
 */
@Suppress("LongParameterList") // What each screen that lists activities varies.
fun LazyListScope.activityItems(
    activities: ImmutableList<ActivityView>,
    viewerDid: String,
    authorClickable: Boolean,
    revealed: Revealed,
    gap: Dp = 0.dp,
    onEvent: (ActivityListEvent) -> Unit,
) {
    items(activities, key = { it.uri.value }) { activity ->
        ActivityListCard(activity, viewerDid, authorClickable, revealed, onEvent, Modifier.padding(bottom = gap))
    }
}

/** One of [activityItems]' cards, for a lazy list that mixes activities with other items (the timeline's import cards). */
@Composable
@Suppress("LongParameterList") // As activityItems.
fun ActivityListCard(
    activity: ActivityView,
    viewerDid: String,
    authorClickable: Boolean,
    revealed: Revealed,
    onEvent: (ActivityListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActivityCard(
        activity = activity,
        onClick = { onEvent(ActivityListEvent.Open(activity)) },
        onAuthorClick = { onEvent(ActivityListEvent.OpenAuthor(activity)) }.takeIf { authorClickable },
        onOpenMedia = { onEvent(ActivityListEvent.OpenMedia(activity, it)) },
        onLike = { onEvent(ActivityListEvent.ToggleLike(activity)) }.takeUnless { activity.author.did.value == viewerDid },
        isOwn = activity.author.did.value == viewerDid,
        revealed = activity.uri.value in revealed,
        onReveal = { revealed.eventSink(activity.uri.value) },
        modifier = modifier,
    )
}
