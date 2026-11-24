package io.github.solcott.activitytracker.app.interactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.data.feed.CommentCountMoves
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * How far the viewer's comment actions, on this screen or any other, have moved [subject]'s comment count since [view] loaded
 * ([CommentCountMoves]). [view] is the very view loaded, not an equal one: a reload's answer, equal or not, already counts the changes the
 * AppView hasn't caught up with, so the count starts again from it. Nothing has moved while [view] is null.
 */
@Composable
fun rememberCommentCountMoved(subject: AtUri, view: Any?, moves: CommentCountMoves): Int {
    val all by moves.moves.collectAsState()
    val loaded = rememberRetained { LoadedMark() }
    if (view == null) return 0
    if (loaded.view !== view) {
        loaded.view = view
        loaded.mark = moves.mark()
    }
    return moves.since(subject, loaded.mark, all)
}

/** The view a screen last showed, and where the moves stood when it loaded. Plain fields: they're read and set only while composing. */
private class LoadedMark {
    var view: Any? = null
    var mark: Int = 0
}

/**
 * How many comment actions the viewer has made on [subject] this session, from any screen: a page showing [subject]'s comments in brief
 * loads its first page again when this changes, since a comment the AppView lists by then is no longer the viewer's just-sent one.
 */
@Composable
fun rememberCommentChanges(subject: AtUri, moves: CommentCountMoves): Int {
    val all by moves.moves.collectAsState()
    return all.count { it.subject == subject }
}
