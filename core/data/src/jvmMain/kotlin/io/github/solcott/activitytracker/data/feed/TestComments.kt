package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ThreadView

/**
 * For tests: what a fake AppView answers `getComments` on [activity] with, from all its [comments] oldest first. With [uris], just those
 * (lookup mode). With [root], that thread's replies, [limit] at a time after [cursor] (thread mode). Otherwise pages of threads, each with
 * its first three replies: a comment naming a root is a reply in that thread, and a thread whose top-level comment isn't among [comments]
 * is a placeholder. Cursors are plain offsets. The desktop app never uses it.
 */
@Suppress("LongParameterList") // getComments' parameters.
fun fakeGetComments(
    activity: AtUri,
    comments: List<CommentView>,
    uris: List<String>? = null,
    root: String? = null,
    cursor: String? = null,
    limit: Int? = null,
): GetComments.Output {
    val from = cursor?.toInt() ?: 0
    val size = limit ?: Paging.DEFAULT_LIMIT
    val threads = comments.groupBy { (it.root?.uri ?: it.uri).value }
    return when {
        uris != null -> GetComments.Output(activity, comments.filter { it.uri.value in uris })
        root != null -> {
            val replies = threads[root].orEmpty().filter { it.root != null }
            GetComments.Output(activity, replies.drop(from).take(size), (from + size).takeIf { it < replies.size }?.toString())
        }
        else -> {
            val views = threads.entries.drop(from).take(size).map { (thread, listed) -> fakeThread(thread, listed) }
            GetComments.Output(activity, views.mapNotNull { it.root }, (from + size).takeIf { it < threads.size }?.toString(), views)
        }
    }
}

private fun fakeThread(thread: String, listed: List<CommentView>): ThreadView {
    val replies = listed.filter { it.root != null }
    return ThreadView(
        AtUri(thread),
        root = listed.firstOrNull { it.root == null },
        replies = replies.take(FIRST_REPLIES),
        replyCount = replies.size,
        repliesCursor = FIRST_REPLIES.takeIf { replies.size > it }?.toString(),
    )
}

private const val FIRST_REPLIES = 3
