package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.interactionErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The detail screen's comment actions: sending [draft] and deleting the viewer's own comments, from the screen's [comments] list (changed
 * with [setComments]). Why the last one failed goes to [onError], and null clears it.
 */
@Suppress("LongParameterList") // The screen state the actions read and change.
internal class CommentActions(
    private val draft: TextFieldState,
    private val flights: SingleFlight,
    private val activities: ActivityRepository,
    private val comments: () -> ImmutableList<CommentView>,
    private val setComments: (ImmutableList<CommentView>) -> Unit,
    private val onError: (UiError?) -> Unit,
) {
    /** Handles [event] on [activity] if it's a comment event. */
    fun on(event: ActivityDetailEvent, activity: ActivityView) {
        when (event) {
            ActivityDetailEvent.SendComment -> send(activity)
            is ActivityDetailEvent.DeleteComment -> delete(activity, event.uri)
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
            flights.launch(ActivityDetailEvent.SendComment) {
                runSuspendCatching { activities.comment(activity, text) }
                    .onFailure { e ->
                        val refused = interactionErrorOf(e) ?: return@onFailure
                        if (draft.text.isEmpty()) draft.setTextAndPlaceCursorAtEnd(text)
                        onError(refused)
                    }
            }
        }
    }

    /**
     * Deletes the viewer's comment [uri] on [activity]. It leaves the list at once, so a repeat finds nothing to delete; a failure puts it
     * back where it was and is reported.
     */
    private fun delete(activity: ActivityView, uri: AtUri) {
        val comment = comments().firstOrNull { it.uri == uri } ?: return
        val index = comments().indexOf(comment)
        setComments((comments() - comment).toImmutableList())
        onError(null)
        flights.launch(uri) {
            runSuspendCatching { activities.deleteComment(activity, comment) }
                .onFailure { e ->
                    setComments(comments().toMutableList().apply { add(index.coerceAtMost(size), comment) }.toImmutableList())
                    onError(UiError.of(UiError.Kind.DeleteComment, e))
                }
        }
    }
}
