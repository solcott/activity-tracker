package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// The comment list several screens show (feed/comments): an activity's on its page, a post's on its page (feed/post-page). A host puts a
// CommentsState in its State, and the shared UI sends CommentEvents to its sink.

/**
 * A subject's comments as a screen shows them: [paging] and [sent] as the viewer left them, the field ([draft], answering [replyingTo]),
 * and why the last send, delete, hide or block failed ([error]).
 *
 * @property count the subject's comment count, already moved by the viewer's own changes the AppView hasn't caught up with.
 * @property author the subject's author, whose hides count: "Hidden by" names them to a commenter.
 * @property isOwn the subject is the viewer's own, so they may hide others' comments on it.
 */
@Immutable
data class CommentsState(
    val paging: CommentsPaging,
    val sent: ImmutableList<SentComment>,
    val draft: TextFieldState,
    val replyingTo: CommentView?,
    val viewerDid: Did,
    val author: ProfileViewBasic,
    val isOwn: Boolean,
    val count: Long,
    val error: UiError? = null,
    val eventSink: (CommentEvent) -> Unit = {},
)

/** What the viewer does to a subject's comments, from the shared comment list. */
sealed interface CommentEvent {
    /** Loads the next page of threads. */
    data object LoadMore : CommentEvent

    /** Loads again what failed: the first page, or the page that failed. */
    data object Retry : CommentEvent

    /** Sends the field's text, as a reply to the comment it answers when set. */
    data object Send : CommentEvent

    /** Answers the listed comment or reply [uri] from the field. */
    data class ReplyTo(val uri: AtUri) : CommentEvent

    /** The field goes back to a top-level comment, keeping its text. */
    data object CancelReply : CommentEvent

    /** Loads more of [thread]'s replies, or again the page that failed. */
    data class LoadMoreReplies(val thread: AtUri) : CommentEvent

    /** Deletes the viewer's own comment [uri]. */
    data class Delete(val uri: AtUri) : CommentEvent

    /** Hides someone else's comment [uri] on the viewer's own subject. */
    data class Hide(val uri: AtUri) : CommentEvent

    data class Unhide(val uri: AtUri) : CommentEvent

    /** Blocks the author of someone else's comment [uri]. */
    data class BlockAuthor(val uri: AtUri) : CommentEvent

    /** Mutes the author of someone else's comment [uri]. */
    data class MuteAuthor(val uri: AtUri) : CommentEvent
}

/** A subject's comment threads, loaded a page at a time as the viewer scrolls to the end. */
@Immutable
data class CommentsPaging(
    /** The threads loaded so far, as the viewer left them. */
    val items: ImmutableList<CommentThread> = persistentListOf(),
    /** The first page's load: a first load, a reload, or [CommentEvent.Retry] after either failed. */
    val refresh: LoadStatus = LoadStatus.Loading,
    /** The latest next page's load; [CommentEvent.Retry] tries a failed one again. */
    val append: LoadStatus = LoadStatus.Idle,
    /** Whether a first page has answered, so empty [items] means there are no comments. */
    val hasAnswer: Boolean = false,
    /** Whether [CommentEvent.LoadMore] would load a page now. */
    val canLoadMore: Boolean = false,
    /** Every page is loaded. */
    val endReached: Boolean = false,
)

/**
 * A thread, as the viewer left it: a top-level comment and its replies, indented under it. Threads are one level deep, so a reply to a
 * reply is listed here too.
 */
@Immutable
data class CommentThread(
    /** The top-level comment's URI. */
    val uri: AtUri,
    /** The top-level comment; null for the placeholder shown when it isn't listed for the viewer, which says nothing more about it. */
    val root: CommentView?,
    /** The replies loaded so far, oldest first: its first few, then each page of more the viewer asked for. */
    val replies: ImmutableList<CommentView>,
    /** The viewer's replies in this thread the AppView doesn't list yet, after [replies]. */
    val sentReplies: ImmutableList<SentComment> = persistentListOf(),
    /** Replies not loaded yet: "View n more replies", which sends [CommentEvent.LoadMoreReplies]. */
    val moreReplies: Int = 0,
    val repliesState: RepliesState = RepliesState.Idle,
)

/** Loading more of a thread's replies. */
@Immutable
sealed interface RepliesState {
    data object Idle : RepliesState

    data object Loading : RepliesState

    /** The last page couldn't be loaded; [CommentEvent.LoadMoreReplies] tries it again. */
    data class Failed(val error: UiError) : RepliesState
}

/** Who liked a subject, or reposted a post (feed/reposts), once the viewer has shown the list. */
@Immutable
data class LikesList(
    val athletes: ImmutableList<ProfileViewBasic>,
    val isLoading: Boolean,
    val error: UiError?,
)
