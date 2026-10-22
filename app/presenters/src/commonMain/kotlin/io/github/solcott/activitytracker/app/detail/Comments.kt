package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalBlocks
import io.github.solcott.activitytracker.data.feed.LocalComments
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.circuit.Paging
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasAnswer
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.pageOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow

/** The detail screen's comments: [paging] and [sent] as the viewer left them. */
@Suppress("LongParameterList") // The comments as shown, how to page them, and what the viewer has changed since.
internal class Comments(
    val paging: CommentsPaging,
    /** The viewer's top-level comments the AppView doesn't list yet; their sent replies are in [paging]'s threads. */
    val sent: ImmutableList<SentComment>,
    /** Loads the next page, unless one is loading, the last failed or none is left. */
    val loadMore: () -> Unit,
    /** Loads again what failed, and only that: the first page, or the page that failed. */
    val retry: () -> Unit,
    /** Loads the next page of a thread's replies, or again only the one that failed, unless one is loading or none is left. */
    val loadMoreReplies: (thread: AtUri) -> Unit,
    /** The signed-in athlete. */
    val viewer: Did,
    private val local: LocalComments,
    private val blocks: LocalBlocks,
) {
    /** How many of the listed comments and replies by [author] are counted (not hidden): what blocking or muting them takes off. */
    fun countedBy(author: Did): Int =
        paging.items.sumOf { thread ->
            (listOfNotNull(thread.root) + thread.replies).count { it.author.did == author && it.hidden != true }
        }

    /**
     * The listed comment or reply [uri], as shown, unless the viewer has deleted it or blocked or muted its author since: a repeated delete
     * or block can arrive after the first finished but before the list shown here caught up.
     */
    fun listed(uri: AtUri): CommentView? =
        paging.items
            .firstNotNullOfOrNull { thread -> thread.root?.takeIf { it.uri == uri } ?: thread.replies.firstOrNull { it.uri == uri } }
            ?.takeUnless { isDeleted(uri) || blocks.hidesCommentsBy(it.author.did) }

    /** Whether the viewer has deleted [uri], read now rather than from the list as last shown. */
    private fun isDeleted(uri: AtUri): Boolean = local.entries.value[uri]?.want == LocalComments.Want.Gone

    /** Whether [uri] is a comment or reply the viewer sent that the AppView doesn't list yet, and hasn't deleted since. */
    fun isSent(uri: AtUri): Boolean =
        (sent.any { it.uri == uri } || paging.items.any { t -> t.sentReplies.any { it.uri == uri } }) && !isDeleted(uri)
}

/**
 * [activity]'s comment threads, the first page loaded with each [loadKey] and the next whenever asked, after the last loaded, and each
 * thread's further replies a page at a time when asked. Both are retained with the page, so coming back shows them at once, and a failed
 * page is tried again alone (feed/comments). A new [loadKey] keeps the threads listed until its first page answers, and only then starts
 * each thread's replies again from those it came with. The AppView's pages are kept as it served them; the viewer's own changes
 * ([FeedRepository.localComments]) are laid over them as they're made, so a send, delete, hide or unhide shows at once, on whichever page
 * or thread.
 */
@Composable
internal fun rememberComments(activity: AtUri, viewer: Did, loadKey: Int, feed: FeedRepository): Comments {
    val local = feed.localComments
    // A change, from this screen or any other, shows at once; so does a block or mute.
    val changes by local.entries.collectAsState()
    val blocked by feed.localBlocks.entries.collectAsState()
    val threads =
        rememberRetainedPaging(activity, loadKey, idOf = ThreadView::uri) { cursor -> flow { emit(feed.commentsPage(activity, cursor)) } }
    val served = threads.state
    // The load whose first page has answered. Each thread's replies start again from the thread as served only then, so a reload keeps
    // the replies loaded before until the threads it lists replace them.
    var answered by rememberRetained { mutableIntStateOf(loadKey) }
    SideEffect { if (served.hasLoaded && served.refresh is LoadStatus.Idle) answered = loadKey }
    val replies = served.items.associate { thread -> thread.uri to key(thread.uri) { rememberReplies(activity, thread, answered, feed) } }
    val replyStates = served.items.map { replies.getValue(it.uri).state }

    val sent = remember(changes) { local.sent(activity) }
    val shown =
        remember(changes, blocked, served, replyStates, sent) {
            val sentReplies = sent.mapNotNull { s -> s.root?.let { it to s } }.groupBy({ it.first }, { it.second })
            // The changes as collected, never the live ones: a change undone before the next collection would stay shown.
            shownThreads(served.items, replyStates, sentReplies) { feed.localBlocks.applyTo(local.applyTo(it, changes), blocked) }
                .toImmutableList()
        }
    return Comments(
        paging =
            CommentsPaging(
                items = shown,
                refresh = served.refresh,
                append = served.append,
                hasAnswer = served.hasAnswer,
                canLoadMore = served.canLoadMore,
                endReached = served.endReached,
            ),
        sent = sent.filter { it.root == null }.toImmutableList(),
        loadMore = threads::loadMore,
        retry = threads::retry,
        loadMoreReplies = { uri ->
            val thread = replies[uri]
            when {
                thread == null -> Unit
                thread.state.append is LoadStatus.Failed -> thread.retry()
                shown.hasMoreReplies(uri) -> thread.loadMore()
            }
        },
        viewer = viewer,
        local = local,
        blocks = feed.localBlocks,
    )
}

/** [thread]'s replies: those it came with, then each page of more asked for. A new [loadKey] starts them again from [thread] as served. */
@Composable
private fun rememberReplies(activity: AtUri, thread: ThreadView, loadKey: Int, feed: FeedRepository): Paging<CommentView, Unit> =
    rememberRetainedPaging(
        thread.uri,
        loadKey,
        idOf = CommentView::uri,
        initial = Outcome.Data(pageOf(thread.replies, thread.repliesCursor), Origin.Network),
    ) { cursor ->
        // Started from the replies the thread came with, it only ever asks for a page after them.
        flow { if (cursor != null) emit(feed.repliesPage(activity, thread.uri, cursor)) }
    }

/**
 * The loaded [threads] as the viewer left them: each with its [replies] loaded so far, [asLeft] laid over it (the viewer's changes,
 * [LocalComments.applyTo], and blocked and muted accounts taken away), and the viewer's [sentReplies] in it at its end. A thread with
 * nothing left to show (its top-level comment deleted here, no replies) is dropped.
 */
private fun shownThreads(
    threads: List<ThreadView>,
    replies: List<PagedState<CommentView, Unit>>,
    sentReplies: Map<AtUri, List<SentComment>>,
    asLeft: (ThreadView) -> ThreadView,
): List<CommentThread> =
    threads.zip(replies).mapNotNull { (served, loaded) ->
        // Until its holder has started from them (the frame it first composes), the replies are those the thread came with.
        val seeded = loaded.hasLoaded
        val shown = asLeft(served.copy(replies = if (seeded) loaded.items else served.replies))
        val sent = sentReplies[served.uri].orEmpty()
        // Replies left to load: none once the last page is in, else at least one while there's a cursor.
        val end = if (seeded) loaded.endReached else served.repliesCursor == null
        val left = if (!end) (shown.replyCount - shown.replies.size).coerceAtLeast(1) else 0
        val listed = shown.root != null || shown.replies.isNotEmpty()
        if (!listed && left == 0 && sent.isEmpty()) return@mapNotNull null
        CommentThread(
            uri = served.uri,
            root = shown.root,
            replies = shown.replies.toImmutableList(),
            sentReplies = sent.toImmutableList(),
            moreReplies = left,
            repliesState =
                when (val append = loaded.append) {
                    is LoadStatus.Failed -> RepliesState.Failed(UiError(UiError.Kind.LoadReplies, append.error))
                    LoadStatus.Loading -> RepliesState.Loading
                    LoadStatus.Idle -> RepliesState.Idle
                },
        )
    }

/** Whether thread [uri] has replies left to load. */
private fun List<CommentThread>.hasMoreReplies(uri: AtUri): Boolean = any { it.uri == uri && it.moreReplies > 0 }
