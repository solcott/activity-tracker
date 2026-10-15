package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalComments
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The detail screen's comments: [paging] and [sent] as the viewer left them. */
internal class Comments(
    val paging: CommentsPaging,
    /** The viewer's top-level comments the AppView doesn't list yet; their sent replies are in [paging]'s threads. */
    val sent: ImmutableList<SentComment>,
    /** Loads the next page, unless one is loading, the last failed or none is left. */
    val loadMore: () -> Unit,
    /** Loads again the page that failed. */
    val retry: () -> Unit,
    /** Loads the next page of a thread's replies, or again the one that failed, unless one is loading or none is left. */
    val loadMoreReplies: (thread: AtUri) -> Unit,
) {
    /** The listed comment or reply [uri], as shown. */
    fun listed(uri: AtUri): CommentView? =
        paging.items.firstNotNullOfOrNull { thread ->
            thread.root?.takeIf { it.uri == uri } ?: thread.replies.firstOrNull { it.uri == uri }
        }

    /** Whether [uri] is a comment or reply the viewer sent that the AppView doesn't list yet. */
    fun isSent(uri: AtUri): Boolean = sent.any { it.uri == uri } || paging.items.any { t -> t.sentReplies.any { it.uri == uri } }
}

/**
 * [activity]'s comment threads, the first page loaded with each [loadKey] and the next whenever asked, after the last loaded, and each
 * thread's further replies a page at a time when asked. The AppView's pages are kept as it served them; the viewer's own changes
 * ([FeedRepository.localComments]) are laid over them as they're made, so a send, delete, hide or unhide shows at once, on whichever page
 * or thread.
 */
@Composable
internal fun rememberComments(activity: AtUri, loadKey: Int, feed: FeedRepository, flights: SingleFlight): Comments {
    val local = feed.localComments
    // A change, from this screen or any other, shows at once.
    val changes by local.entries.collectAsState()
    var pages by remember { mutableStateOf(Pages()) }
    var firstLoading by remember { mutableStateOf(true) }

    suspend fun load(after: Pages) {
        val result = runSuspendCatching { feed.comments(activity, after.cursor) }
        // A page for an earlier load (a reload started meanwhile) is dropped.
        if (pages.generation == after.generation) pages = pages.withPage(after, result)
    }

    suspend fun loadReplies(thread: ThreadView, generation: Int) {
        val cursor = pages.replies[thread.uri]?.cursor ?: thread.repliesCursor ?: return
        val result = runSuspendCatching { feed.comments(activity, cursor, root = thread.uri) }
        if (pages.generation == generation) pages = pages.withReplies(thread, cursor, result)
    }

    LaunchedEffect(loadKey) {
        val fresh = Pages(generation = loadKey)
        pages = fresh
        firstLoading = true
        load(fresh)
        firstLoading = false
    }

    val sent = remember(changes) { local.sent(activity) }
    val threads =
        remember(changes, pages, sent) {
            shownThreads(pages, local, sent.mapNotNull { s -> s.root?.let { it to s } }.groupBy({ it.first }, { it.second }))
        }
    // Rebuilt only when the threads or which of them are loading replies change, not on every recomposition (each keystroke in the field).
    val loadingReplies = threads.filter { flights.isRunning(ActivityDetailEvent.LoadMoreReplies(it.uri)) }.map { it.uri }
    val items = remember(threads, loadingReplies) { threads.loading(loadingReplies) }
    return Comments(
        paging =
            CommentsPaging(
                items = items,
                isLoading = firstLoading || flights.isRunning(ActivityDetailEvent.LoadMoreComments),
                endReached = pages.end,
                error = pages.error,
            ),
        sent = sent.filter { it.root == null }.toImmutableList(),
        loadMore = {
            val current = pages
            if (!firstLoading && !current.end && current.error == null)
                flights.launch(ActivityDetailEvent.LoadMoreComments) { load(current) }
        },
        retry = {
            val current = pages.copy(error = null)
            if (pages.error != null) {
                pages = current
                flights.launch(ActivityDetailEvent.LoadMoreComments) { load(current) }
            }
        },
        loadMoreReplies = { uri ->
            val thread = pages.threads.firstOrNull { it.uri == uri }?.takeIf { threads.hasMoreReplies(uri) }
            val generation = pages.generation
            thread?.let { flights.launch(ActivityDetailEvent.LoadMoreReplies(uri)) { loadReplies(it, generation) } }
        },
    )
}

/**
 * The loaded threads as the viewer left them: each with the replies loaded for it since, [LocalComments.applyTo] laid over it, and the
 * viewer's [sentReplies] in it at its end. A thread with nothing left to show (its top-level comment deleted here, no replies) is dropped.
 */
private fun shownThreads(pages: Pages, local: LocalComments, sentReplies: Map<AtUri, List<SentComment>>): List<CommentThread> =
    pages.threads.mapNotNull { served ->
        val more = pages.replies[served.uri]
        val shown = local.applyTo(served.copy(replies = served.replies + more?.items.orEmpty()))
        val sent = sentReplies[served.uri].orEmpty()
        // Replies left to load: none once the last page is in, else at least one while there's a cursor.
        val hasMore = if (more != null) !more.loadedAll else served.repliesCursor != null
        val left = if (hasMore) (shown.replyCount - shown.replies.size).coerceAtLeast(1) else 0
        val listed = shown.root != null || shown.replies.isNotEmpty()
        if (!listed && left == 0 && sent.isEmpty()) return@mapNotNull null
        CommentThread(
            uri = served.uri,
            root = shown.root,
            replies = shown.replies.toImmutableList(),
            sentReplies = sent.toImmutableList(),
            moreReplies = left,
            repliesState = more?.error?.let(RepliesState::Failed) ?: RepliesState.Idle,
        )
    }

/** [this], with the [loading] threads loading their replies. */
private fun List<CommentThread>.loading(loading: List<AtUri>): ImmutableList<CommentThread> = map {
    if (it.uri in loading) it.copy(repliesState = RepliesState.Loading) else it
}
    .toImmutableList()

/** Whether thread [uri] has replies left to load. */
private fun List<CommentThread>.hasMoreReplies(uri: AtUri): Boolean = any { it.uri == uri && it.moreReplies > 0 }

/** The AppView's pages of threads loaded for one load of the screen ([generation]), as it served them. */
private data class Pages(
    val generation: Int = -1,
    val threads: List<ThreadView> = emptyList(),
    /** After the last page loaded; null before the first, and once [end] is reached. */
    val cursor: String? = null,
    val end: Boolean = false,
    val error: UiError? = null,
    /** Each thread's replies loaded after its first few, by thread URI. */
    val replies: Map<AtUri, Replies> = emptyMap(),
) {
    /** These pages after the next page, read after [after]: added once each, or why it couldn't be loaded. */
    fun withPage(after: Pages, result: Result<GetComments.Output>): Pages =
        result.fold(
            { page ->
                val known = after.threads.mapTo(mutableSetOf()) { it.uri }
                copy(
                    threads = after.threads + page.threads.orEmpty().filter { it.uri !in known },
                    cursor = page.cursor,
                    end = page.cursor == null,
                    error = null,
                )
            },
            { copy(error = UiError.of(UiError.Kind.LoadComments, it)) },
        )

    /** These pages after a page of [thread]'s replies read at [cursor]: added once each, or why it couldn't be loaded. */
    fun withReplies(thread: ThreadView, cursor: String, result: Result<GetComments.Output>): Pages {
        val before = replies[thread.uri]
        val after =
            result.fold(
                { page ->
                    val known = (thread.replies + before?.items.orEmpty()).mapTo(mutableSetOf()) { it.uri }
                    Replies(
                        before?.items.orEmpty() + page.comments.filter { it.uri !in known },
                        page.cursor,
                        loadedAll = page.cursor == null,
                    )
                },
                { (before ?: Replies(cursor = cursor)).copy(error = UiError.of(UiError.Kind.LoadReplies, it)) },
            )
        return copy(replies = replies + (thread.uri to after))
    }
}

/** A thread's replies loaded after the ones that came with it, [cursor] after the last, until [loadedAll]. */
private data class Replies(
    val items: List<CommentView> = emptyList(),
    val cursor: String? = null,
    val loadedAll: Boolean = false,
    val error: UiError? = null,
)
