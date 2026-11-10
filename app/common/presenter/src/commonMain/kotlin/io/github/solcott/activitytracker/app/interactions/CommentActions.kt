package io.github.solcott.activitytracker.app.interactions

import androidx.compose.runtime.MutableState
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.interactionErrorOf
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.InteractionSubject
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.clearText
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.CoroutineScope

/**
 * A screen's comment actions on a subject (an activity or a post): sending [draft], as a reply to [replyingTo] when set
 * ([CommentEvent.ReplyTo]), deleting the viewer's own comments, and hiding or unhiding others' comments on the viewer's own subject, from
 * the screen's [comments]. Each shows through [FeedRepository.localComments] as soon as it's made, and is taken back there if it can't be;
 * why the last one failed goes to [onError], and null clears it. How far each moves the subject's comment count goes to [onCountMoved] at
 * once, with the list, and back again if it fails.
 */
@Suppress("LongParameterList") // The screen's state it acts on, and how to report back.
class CommentActions(
    private val draft: TextFieldState,
    private val flights: SingleFlight,
    private val activities: ActivityRepository,
    private val blocks: BlockRepository,
    private val comments: Comments,
    /** The comment the draft answers; null for a top-level comment. */
    private val replyingTo: MutableState<CommentView?>,
    private val onError: (UiError?) -> Unit,
    private val onCountMoved: (Int) -> Unit,
) {
    /** Handles [event] on [subject], paging included. */
    fun on(event: CommentEvent, subject: InteractionSubject) {
        when (event) {
            CommentEvent.LoadMore -> comments.loadMore()
            CommentEvent.Retry -> comments.retry()
            CommentEvent.Send -> send(subject)
            // Only a listed comment can be answered: it has a CID, which a sent one doesn't. Nor a hidden one.
            is CommentEvent.ReplyTo -> comments.listed(event.uri)?.takeIf { it.hidden != true }?.let { replyingTo.value = it }
            CommentEvent.CancelReply -> replyingTo.value = null
            is CommentEvent.LoadMoreReplies -> comments.loadMoreReplies(event.thread)
            is CommentEvent.Delete -> delete(subject, event.uri)
            is CommentEvent.Hide -> setHidden(subject, event.uri, hidden = true)
            is CommentEvent.Unhide -> setHidden(subject, event.uri, hidden = false)
            is CommentEvent.BlockAuthor -> leaveOut(event.uri, block = true)
            is CommentEvent.MuteAuthor -> leaveOut(event.uri, block = false)
        }
    }

    /**
     * Sends [draft] on [subject], as a reply to [replyingTo] when set, and clears it, unless it's blank or a comment is already sending.
     * Once sent, the field goes back to top-level comments, unless the viewer has chosen another comment to answer since. A comment the
     * session can't give is reported, its text put back unless something new has been typed since, and still answers [replyingTo].
     */
    private fun send(subject: InteractionSubject) {
        val text = draft.text.toString()
        // Checked before clearing the draft, so a dropped repeat can't lose what was typed.
        if (text.isNotBlank() && !flights.isRunning(CommentEvent.Send)) {
            val replyTo = replyingTo.value
            draft.clearText()
            onError(null)
            launchCounted(CommentEvent.Send, 1) {
                runSuspendCatching { activities.comment(subject, text, replyTo) }
                    .onSuccess { if (replyingTo.value?.uri == replyTo?.uri) replyingTo.value = null }
                    .onFailure { e ->
                        onCountMoved(-1)
                        val refused = interactionErrorOf(e) ?: return@onFailure
                        if (draft.text.isEmpty()) draft.setTextAndPlaceCursorAtEnd(text)
                        onError(refused)
                    }
            }
        }
    }

    /**
     * Deletes the viewer's comment [uri] on [subject], listed or just sent. It leaves the list at once, so a repeat finds nothing to
     * delete; a failure puts it back and is reported.
     */
    private fun delete(subject: InteractionSubject, uri: AtUri) {
        val listed = comments.listed(uri)
        if (listed == null && !comments.isSent(uri)) return
        onError(null)
        stopReplyingTo(uri)
        val hidden = listed?.hidden == true
        // A hidden comment was never counted.
        val moved = if (hidden) 0 else -1
        launchCounted(uri, moved) {
            runSuspendCatching { activities.deleteComment(subject, uri, hidden) }
                .onFailure { e ->
                    onCountMoved(-moved)
                    onError(UiError.of(UiError.Kind.DeleteComment, e))
                }
        }
    }

    /**
     * Hides or unhides comment [uri] on the viewer's own [subject]. It's marked so at once, so the count moves with it; a repeat while the
     * first is running is dropped. A failure puts the mark back and is reported, a session that can't hide there asked to sign in again.
     */
    private fun setHidden(subject: InteractionSubject, uri: AtUri, hidden: Boolean) {
        val comment = comments.listed(uri)?.takeIf { (it.hidden == true) != hidden && !flights.isRunning(uri) } ?: return
        onError(null)
        // A hidden comment can't be replied to.
        if (hidden) stopReplyingTo(uri)
        val moved = if (hidden) -1 else 1
        launchCounted(uri, moved) {
            runSuspendCatching { activities.setCommentHidden(subject, comment, hidden) }
                .onSuccess { changed -> if (!changed) onCountMoved(-moved) }
                .onFailure { e ->
                    onCountMoved(-moved)
                    val kind = if (hidden) UiError.Kind.HideComment else UiError.Kind.UnhideComment
                    onError(interactionErrorOf(e) ?: UiError.of(kind, e))
                }
        }
    }

    /**
     * Blocks ([block]) or mutes the author of someone else's listed comment [uri]. Their listed comments leave the list at once, through
     * [FeedRepository.localBlocks], and the count drops by those it counted; a failure brings them back and is reported, a session that
     * can't write blocks there asked to sign in again.
     */
    private fun leaveOut(uri: AtUri, block: Boolean) {
        val author = comments.listed(uri)?.author?.did?.takeUnless { it == comments.viewer } ?: return
        val key = if (block) CommentEvent.BlockAuthor(uri) else CommentEvent.MuteAuthor(uri)
        onError(null)
        val counted = comments.countedBy(author)
        if (replyingTo.value?.author?.did == author) replyingTo.value = null
        launchCounted(key, -counted) {
            runSuspendCatching { if (block) blocks.setBlocked(author, true) else blocks.setMuted(author, true) }
                .onFailure { e ->
                    onCountMoved(counted)
                    onError(uiErrorOf(if (block) UiError.Kind.UpdateBlock else UiError.Kind.UpdateMute, e))
                }
        }
    }

    /**
     * Starts [block] under [key], unless it's already running, moving the count by [moved] first: the work can change the list on another
     * thread before a move made after starting it would land, and the count must never trail the list. A dropped repeat moves nothing.
     */
    private fun launchCounted(key: Any, moved: Int, block: suspend CoroutineScope.() -> Unit) {
        if (flights.isRunning(key)) return
        onCountMoved(moved)
        flights.launch(key, block)
    }

    /** A comment being deleted or hidden can't be answered: the field goes back to a top-level comment, keeping its text. */
    private fun stopReplyingTo(uri: AtUri) {
        if (replyingTo.value?.uri == uri) replyingTo.value = null
    }
}
