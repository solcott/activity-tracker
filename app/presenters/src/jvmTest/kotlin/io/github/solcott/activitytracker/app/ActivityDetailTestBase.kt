package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailPage
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.CommentsPaging
import io.github.solcott.activitytracker.app.detail.LikesList
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.uistate.LoadStatus

/** The top-level comments of the listed threads, leaving out placeholders. */
internal val CommentsPaging.roots: List<CommentView>
    get() = items.mapNotNull { it.root }

/** Whether the first page, a reload or the next page of threads is loading. */
internal val CommentsPaging.isLoading: Boolean
    get() = refresh is LoadStatus.Loading || append is LoadStatus.Loading

/** Why the first page, a reload or the next page of threads failed, as the page shows it; null when nothing failed. */
internal val CommentsPaging.error: UiError?
    get() = ((refresh as? LoadStatus.Failed) ?: (append as? LoadStatus.Failed))?.let { UiError(UiError.Kind.LoadComments, it.error) }

/** The loaded page; null until the activity first loads. */
internal fun ActivityDetailState.page(): ActivityDetailPage? = content.data

/** The detail screen's fakes, shared by its presenter tests: [ActivityTestBase]'s served activity, and the page's presenter over it. */
open class ActivityDetailTestBase : ActivityTestBase() {
    protected val screen = ActivityDetailScreen(activityUri)

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

    protected fun ActivityDetailState.loaded() = page()

    protected suspend fun detail(r: Repos, block: suspend CircuitReceiveTurbine<ActivityDetailState>.() -> Unit) =
        presenterTestOf(presenterOf(r), block = block)

    /** The page's presenter for [on], on [r]'s repositories. */
    protected fun presenterOf(r: Repos, on: ActivityDetailScreen = screen): @Composable () -> ActivityDetailState = {
        ActivityDetailPresenter(
            on,
            FakeNavigator(on),
            r.feed,
            r.activities,
            r.profiles,
            localLikes,
            r.manager,
            r.exporter,
            r.units,
            r.suggestions,
            r.blocks,
            r.reports,
            r.segments,
        )
    }

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() = awaitUntil { it.page() != null }.page()!!

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLikes(done: (LikesList) -> Boolean): ActivityDetailPage {
        var state = awaitLoaded()
        while (state.likesList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }

    protected companion object {
        /** The AppView's page size for `getComments` when none is asked for. */
        const val PAGE = 30
    }
}
