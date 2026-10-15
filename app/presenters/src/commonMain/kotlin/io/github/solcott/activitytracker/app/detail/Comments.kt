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
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The detail screen's comments: [paging] and [sent] as the viewer left them. */
internal class Comments(
    val paging: CommentsPaging,
    val sent: ImmutableList<SentComment>,
    /** Loads the next page, unless one is loading, the last failed or none is left. */
    val loadMore: () -> Unit,
    /** Loads again the page that failed. */
    val retry: () -> Unit,
)

/**
 * [activity]'s comments, the first page loaded with each [loadKey] and the next whenever asked, after the last loaded. The AppView's pages
 * are kept as it served them; the viewer's own changes ([FeedRepository.localComments]) are laid over them as they're made, so a send,
 * delete, hide or unhide shows at once, on whichever page.
 */
@Composable
internal fun rememberComments(activity: AtUri, loadKey: Int, feed: FeedRepository, flights: SingleFlight): Comments {
    val local = feed.localComments
    // A change, from this screen or any other, shows at once.
    val changes by local.entries.collectAsState()
    var pages by remember { mutableStateOf(Pages()) }
    var firstLoading by remember { mutableStateOf(true) }

    suspend fun load(after: Pages) {
        runSuspendCatching { feed.comments(activity, after.cursor) }
            .onSuccess { page ->
                // A page for an earlier load (a reload started meanwhile) is dropped.
                if (pages.generation != after.generation) return@onSuccess
                val known = after.items.mapTo(mutableSetOf()) { it.uri }
                pages =
                    after.copy(
                        items = after.items + page.comments.filter { it.uri !in known },
                        cursor = page.cursor,
                        end = page.cursor == null,
                    )
            }
            .onFailure { if (pages.generation == after.generation) pages = after.copy(error = UiError.of(UiError.Kind.LoadComments, it)) }
    }

    LaunchedEffect(loadKey) {
        val fresh = Pages(generation = loadKey)
        pages = fresh
        firstLoading = true
        load(fresh)
        firstLoading = false
    }

    return Comments(
        paging =
            CommentsPaging(
                items = remember(changes, pages.items) { local.applyTo(pages.items).toImmutableList() },
                isLoading = firstLoading || flights.isRunning(ActivityDetailEvent.LoadMoreComments),
                endReached = pages.end,
                error = pages.error,
            ),
        sent = remember(changes) { local.sent(activity).toImmutableList() },
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
    )
}

/** The AppView's pages of comments loaded for one load of the screen ([generation]), as it served them. */
private data class Pages(
    val generation: Int = -1,
    val items: List<CommentView> = emptyList(),
    /** After the last page loaded; null before the first, and once [end] is reached. */
    val cursor: String? = null,
    val end: Boolean = false,
    val error: UiError? = null,
)
