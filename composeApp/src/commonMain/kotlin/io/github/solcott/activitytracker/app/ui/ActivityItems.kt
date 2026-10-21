package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList

/**
 * [activities] as cards keyed by URI, for any screen that lists them inside its own lazy list. Liking is offered on activities not by
 * [viewerDid]. [authorClickable] is false where the author is the page itself, as on a profile. Labeled photos stay blurred until the
 * viewer reveals them on this screen ([revealed]).
 */
fun LazyListScope.activityItems(
    activities: ImmutableList<ActivityView>,
    viewerDid: String,
    authorClickable: Boolean,
    revealed: Revealed,
    onEvent: (ActivityListEvent) -> Unit,
) {
    items(activities, key = { it.uri.value }) { activity ->
        ActivityCard(
            activity = activity,
            onClick = { onEvent(ActivityListEvent.Open(activity)) },
            onAuthorClick = { onEvent(ActivityListEvent.OpenAuthor(activity)) }.takeIf { authorClickable },
            onOpenMedia = { onEvent(ActivityListEvent.OpenMedia(activity, it)) },
            onLike = { onEvent(ActivityListEvent.ToggleLike(activity)) }.takeUnless { activity.author.did.value == viewerDid },
            isOwn = activity.author.did.value == viewerDid,
            revealed = activity.uri.value in revealed,
            onReveal = { revealed.eventSink(activity.uri.value) },
        )
    }
}
