package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.comments.CommentsPresenter
import io.github.solcott.activitytracker.app.comments.CommentsScreen
import io.github.solcott.activitytracker.app.comments.CommentsScreenEvent
import io.github.solcott.activitytracker.app.comments.CommentsScreenState
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.uistate.LoadStatus

/** The top-level comments of the listed threads, leaving out placeholders. */
internal val CommentsPaging.roots: List<CommentView>
    get() = items.mapNotNull { it.root }

/** Whether the first page, a reload or the next page of threads is loading. */
internal val CommentsPaging.isLoading: Boolean
    get() = refresh is LoadStatus.Loading || append is LoadStatus.Loading

/** Why the first page, a reload or the next page of threads failed, as the screen shows it; null when nothing failed. */
internal val CommentsPaging.error: UiError?
    get() = ((refresh as? LoadStatus.Failed) ?: (append as? LoadStatus.Failed))?.let { UiError(UiError.Kind.LoadComments, it.error) }

/** The loaded Comments screen as its tests read it: the comment list's state, and both its sinks. */
class LoadedComments(private val state: CommentsScreenState, private val list: CommentsState) {
    val comments: CommentsPaging
        get() = list.paging

    val sentComments
        get() = list.sent

    val draftComment
        get() = list.draft

    val replyingTo
        get() = list.replyingTo

    val viewerDid
        get() = list.viewerDid

    val isOwn
        get() = list.isOwn

    val commentError
        get() = list.error

    /** The subject's comment count, moved by the viewer's own changes. */
    val count
        get() = list.count

    fun eventSink(event: CommentEvent) = list.eventSink(event)

    fun eventSink(event: CommentsScreenEvent) = state.eventSink(event)
}

/** The loaded screen; null until the subject first loads. */
internal fun CommentsScreenState.page(): LoadedComments? = page?.let { LoadedComments(this, it.comments) }

/** The Comments screen's fakes, shared by its presenter tests: [ActivityTestBase]'s served activity, and the screen's presenter on it. */
open class CommentsTestBase : ActivityTestBase() {
    protected val screen = CommentsScreen(activityUri)

    /** The comments Alice has queued, as (space key or null for the repo, record). */
    protected suspend fun Repos.queuedComments() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT }
            .map { it.space?.substringAfterLast('/') to it.recordJson }

    /** [count] of carol's comments, `3kc000`…, oldest first, with Alice's own at [own] when set. */
    protected fun manyComments(count: Int, own: Int = -1) =
        (0 until count).map { i ->
            val rkey = "3kc" + i.toString().padStart(3, '0')
            if (i == own) comment(staleAlice, alice.value, rkey) else comment(carol, "did:plc:carol", rkey)
        }

    protected fun CommentsScreenState.loaded() = page()

    protected suspend fun commentsOf(r: Repos, block: suspend CircuitReceiveTurbine<CommentsScreenState>.() -> Unit) =
        presenterTestOf(presenterOf(r), block = block)

    /** The screen's presenter for [on], on [r]'s repositories. */
    protected fun presenterOf(r: Repos, on: CommentsScreen = screen): @Composable () -> CommentsScreenState = {
        CommentsPresenter(on, FakeNavigator(on), r.feed, r.activities, r.profiles, r.blocks, r.reports)
    }

    protected suspend fun ReceiveTurbine<CommentsScreenState>.awaitLoaded() = awaitUntil { it.page() != null }.page()!!

    protected companion object {
        /** The AppView's page size for `getComments` when none is asked for. */
        const val PAGE = 30
    }
}
