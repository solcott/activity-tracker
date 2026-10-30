package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/**
 * How far the viewer's sends, deletes, hides and unhides on this screen have moved [base]'s comment count since it loaded. [base] is the
 * very view loaded, not an equal one: a reload's answer, equal or not, already counts the changes the AppView hasn't caught up with.
 */
internal class CommentMoves(val base: ActivityView, val moves: Int)

/** How far [this] moved [view]'s count: none once a reload has replaced it. */
internal fun CommentMoves?.over(view: ActivityView?): Int = this?.takeIf { it.base === view }?.moves ?: 0

/** [this] with [moved] more over [view], the loaded view; none before it has loaded. */
internal fun CommentMoves?.plus(view: ActivityView?, moved: Int): CommentMoves? = view?.let { CommentMoves(it, over(it) + moved) }

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
