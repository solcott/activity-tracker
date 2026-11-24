package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/** [view] as it shows: [rewritten] with the viewer's own edit, their like toggles laid over it, and its count moved by their [moves]. */
internal fun shown(
    view: ActivityView,
    moves: Int,
    rewritten: ((ActivityView) -> ActivityView)?,
    likeOverrides: Map<String, AtUri?>,
): ActivityView {
    val liked = (rewritten?.invoke(view) ?: view).withLikeOverrides(likeOverrides)
    return if (moves == 0) liked else liked.copy(commentCount = (liked.commentCount + moves).coerceAtLeast(0))
}
