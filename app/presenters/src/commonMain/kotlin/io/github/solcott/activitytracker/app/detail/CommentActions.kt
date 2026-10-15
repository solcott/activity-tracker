package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.interactionErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/**
 * The detail screen's comment actions: sending [draft], deleting the viewer's own comments, and hiding or unhiding others' comments on the
 * viewer's own activity, from the screen's [comments]. Each shows through [FeedRepository.localComments] as soon as it's made, and is taken
 * back there if it can't be; why the last one failed goes to [onError], and null clears it. How far each moves the activity's comment count
 * goes to [onCountMoved] at once, with the list, and back again if it fails.
 */
internal class CommentActions(
    private val draft: TextFieldState,
    private val flights: SingleFlight,
    private val activities: ActivityRepository,
    private val comments: Comments,
    private val onError: (UiError?) -> Unit,
    private val onCountMoved: (Int) -> Unit,
) {
    /** Handles [event] on [activity] if it's a comment event, paging included. */
    fun on(event: ActivityDetailEvent, activity: ActivityView) {
        when (event) {
            ActivityDetailEvent.LoadMoreComments -> comments.loadMore()
            ActivityDetailEvent.RetryComments -> comments.retry()
            ActivityDetailEvent.SendComment -> send(activity)
            is ActivityDetailEvent.DeleteComment -> delete(activity, event.uri)
            is ActivityDetailEvent.HideComment -> setHidden(activity, event.uri, hidden = true)
            is ActivityDetailEvent.UnhideComment -> setHidden(activity, event.uri, hidden = false)
            else -> Unit
        }
    }

    /**
     * Sends [draft] on [activity] and clears it, unless it's blank or a comment is already sending. A comment the session can't give is
     * reported and its text put back, unless something new has been typed since.
     */
    private fun send(activity: ActivityView) {
        val text = draft.text.toString()
        // Checked before clearing the draft, so a dropped repeat can't lose what was typed.
        if (text.isNotBlank() && !flights.isRunning(ActivityDetailEvent.SendComment)) {
            draft.clearText()
            onError(null)
            flights
                .launch(ActivityDetailEvent.SendComment) {
                    runSuspendCatching { activities.comment(activity, text) }
                        .onFailure { e ->
                            onCountMoved(-1)
                            val refused = interactionErrorOf(e) ?: return@onFailure
                            if (draft.text.isEmpty()) draft.setTextAndPlaceCursorAtEnd(text)
                            onError(refused)
                        }
                }
                // Counted with the list, only if it started: a dropped repeat moves nothing.
                ?.let { onCountMoved(1) }
        }
    }

    /**
     * Deletes the viewer's comment [uri] on [activity], listed or just sent. It leaves the list at once, so a repeat finds nothing to
     * delete; a failure puts it back and is reported.
     */
    private fun delete(activity: ActivityView, uri: AtUri) {
        val listed = comments.paging.items.firstOrNull { it.uri == uri }
        if (listed == null && comments.sent.none { it.uri == uri }) return
        onError(null)
        val hidden = listed?.hidden == true
        // A hidden comment was never counted.
        val moved = if (hidden) 0 else -1
        flights
            .launch(uri) {
                runSuspendCatching { activities.deleteComment(activity, uri, hidden) }
                    .onFailure { e ->
                        onCountMoved(-moved)
                        onError(UiError.of(UiError.Kind.DeleteComment, e))
                    }
            }
            ?.let { onCountMoved(moved) }
    }

    /**
     * Hides or unhides comment [uri] on the viewer's own [activity]. It's marked so at once, so the count moves with it; a repeat while the
     * first is running is dropped. A failure puts the mark back and is reported, a session that can't hide there asked to sign in again.
     */
    private fun setHidden(activity: ActivityView, uri: AtUri, hidden: Boolean) {
        val comment =
            comments.paging.items.firstOrNull { it.uri == uri }?.takeIf { (it.hidden == true) != hidden && !flights.isRunning(uri) }
                ?: return
        onError(null)
        val moved = if (hidden) -1 else 1
        flights
            .launch(uri) {
                runSuspendCatching { activities.setCommentHidden(activity, comment, hidden) }
                    .onSuccess { changed -> if (!changed) onCountMoved(-moved) }
                    .onFailure { e ->
                        onCountMoved(-moved)
                        val kind = if (hidden) UiError.Kind.HideComment else UiError.Kind.UnhideComment
                        onError(interactionErrorOf(e) ?: UiError.of(kind, e))
                    }
            }
            ?.let { onCountMoved(moved) }
    }
}
