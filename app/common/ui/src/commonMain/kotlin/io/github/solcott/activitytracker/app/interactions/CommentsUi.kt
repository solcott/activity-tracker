package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ErrorRetryRow
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ModerationNotice
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.shownNameLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.comment_cancel_reply
import io.github.solcott.activitytracker.composeApp.resources.comment_hidden
import io.github.solcott.activitytracker.composeApp.resources.comment_hidden_by
import io.github.solcott.activitytracker.composeApp.resources.comment_reply
import io.github.solcott.activitytracker.composeApp.resources.comment_replying_to
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_count
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_placeholder
import io.github.solcott.activitytracker.composeApp.resources.detail_section_comments
import io.github.solcott.activitytracker.composeApp.resources.detail_send_comment
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// A subject's comments, an activity's or a post's (feed/comments): lazy items, threads paged as the viewer scrolls.

/**
 * Asks for the next page of threads when one of the last [LOAD_MORE_AHEAD] threads listed, or anything after them, is shown, while a page
 * can load: none is loading, the last didn't fail and not every page is in. Threads, not rows, since a thread is several rows. The
 * presenter drops a repeat while one loads.
 */
@Composable
fun LoadMoreNearTheEnd(listState: LazyListState, state: CommentsState) {
    val paging = state.paging
    val near by
        remember(listState, paging.items) {
            derivedStateOf {
                val lastThreads = paging.items.takeLast(LOAD_MORE_AHEAD).mapTo(HashSet()) { it.uri.value }
                listState.layoutInfo.visibleItemsInfo.any { item ->
                    val key = item.key as? String ?: return@any false
                    key in TAIL_KEYS || key.startsWith(SENT_KEY) || (key.startsWith(THREAD_KEY) && threadOf(key) in lastThreads)
                }
            }
        }
    val wanted = near && paging.canLoadMore
    SideEffect(wanted, paging.items.size) { if (wanted) state.eventSink(CommentEvent.LoadMore) }
}

/**
 * The comment count (hidden ones left out), then the threads loaded so far, oldest first: each top-level comment, or a placeholder when it
 * isn't listed, with its replies indented under it, the viewer's just-sent replies, and a row to view the rest. Each comment has Reply, and
 * a menu to delete the viewer's own or hide others' on their own subject. Rows are padded [horizontal] from the list's edges. Then the
 * viewer's just-sent comments, a row while more load or when they couldn't, and a field to add one, saying which comment it answers, and
 * why the last one couldn't be sent, deleted, hidden or unhidden. A hidden comment, listed only for the subject's author and its commenter,
 * is dimmed and says so. Report on others' comments opens [reporting]'s sheet.
 */
fun LazyListScope.comments(state: CommentsState, reporting: Reporting, horizontal: Dp = CARD_PADDING) {
    item(key = "comments") { CommentsHeader(state, Modifier.padding(horizontal = horizontal)) }
    val top = Modifier.padding(horizontal = horizontal)
    val reply = Modifier.padding(start = horizontal + REPLY_INDENT, end = horizontal)
    for (thread in state.paging.items) {
        val prefix = THREAD_KEY + thread.uri.value
        item(key = "$prefix|root") {
            val root = thread.root
            if (root == null) CommentUnavailable(top) else CommentRow(root, commentActions(root, state, reporting), state, reporting, top)
        }
        items(thread.replies, key = { "$prefix|r|${it.uri.value}" }) {
            CommentRow(it, commentActions(it, state, reporting), state, reporting, reply)
        }
        items(thread.sentReplies, key = { "$prefix|s|${it.uri.value}" }) { SentCommentRow(it, state, reply) }
        if (thread.moreReplies > 0 || thread.repliesState is RepliesState.Failed) {
            item(key = "$prefix|more") { MoreReplies(thread, state.eventSink, reply) }
        }
    }
    items(state.sent, key = { SENT_KEY + it.uri.value }) { SentCommentRow(it, state, top) }
    item(key = TAIL_KEYS[0]) { CommentsLoading(state, top) }
    item(key = TAIL_KEYS[1]) { CommentField(state, top) }
}

@Composable
private fun CommentsHeader(state: CommentsState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.detail_section_comments), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        val count = state.count.toInt()
        Text(
            pluralStringResource(Res.plurals.detail_comment_count, count, formatWhole(count)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommentRow(
    comment: CommentView,
    actions: ImmutableSet<CommentAction>,
    state: CommentsState,
    reporting: Reporting,
    modifier: Modifier = Modifier,
) {
    val name = shownNameLabel(comment.author)
    CommentRow(
        author = comment.author.shownName,
        text = comment.text,
        hidden = comment.hidden == true,
        actions = actions,
        uri = comment.uri,
        state = state,
        modifier = modifier,
        onReport = { reporting.eventSink(ReportingEvent.Open(ReportTarget.Comment(comment.uri, comment.cid, comment.author.did), name)) },
        decision = ModerationDecision.of(comment.labels, comment.author.labels, isOwn = comment.author.did == state.viewerDid),
    )
}

/** The viewer's comment or reply the AppView doesn't list yet: they can delete it, but not reply to it until it's listed. */
@Composable
private fun SentCommentRow(comment: SentComment, state: CommentsState, modifier: Modifier = Modifier) =
    CommentRow(
        author = comment.author.shownName,
        text = comment.text,
        hidden = false,
        actions = persistentSetOf(CommentAction.Delete),
        uri = comment.uri,
        state = state,
        modifier = modifier,
    )

@Suppress("LongParameterList") // A listed comment or a just-sent one, which share no type.
@Composable
private fun CommentRow(
    author: String,
    text: String,
    hidden: Boolean,
    actions: ImmutableSet<CommentAction>,
    uri: AtUri,
    state: CommentsState,
    modifier: Modifier = Modifier,
    /** Opens the report sheet, for [CommentAction.Report]. */
    onReport: () -> Unit = {},
    /** What its labels and its author's call for: a warning shows above the text. */
    decision: ModerationDecision = ModerationDecision.None,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).alpha(if (hidden) HIDDEN_ALPHA else 1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(author, style = MaterialTheme.typography.labelLarge)
            ModerationNotice(decision, isOwn = false)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (hidden) {
                val athlete = state.author.shownName
                Text(
                    if (state.isOwn) stringResource(Res.string.comment_hidden) else stringResource(Res.string.comment_hidden_by, athlete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (CommentAction.Reply in actions) {
                AppTextButton(onClick = { state.eventSink(CommentEvent.ReplyTo(uri)) }) {
                    Text(stringResource(Res.string.comment_reply), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        val menu = actions.filterTo(mutableSetOf()) { it != CommentAction.Reply }.toImmutableSet()
        if (menu.isNotEmpty()) key(uri) { CommentMenu(uri, author, menu, state.eventSink, onReport = onReport) }
    }
}

/**
 * A spinner while the first page, a reload or the next page of comments loads, or why one couldn't and a way to try again only it
 * (ui/content-loading); nothing otherwise. A failed reload keeps the threads listed above it.
 */
@Composable
private fun CommentsLoading(state: CommentsState, modifier: Modifier = Modifier) {
    val paging = state.paging
    val failed = (paging.refresh as? LoadStatus.Failed) ?: (paging.append as? LoadStatus.Failed)
    when {
        failed != null ->
            ErrorRetryRow(
                UiError(UiError.Kind.LoadComments, failed.error),
                onRetry = { state.eventSink(CommentEvent.Retry) },
                modifier,
            )
        paging.refresh is LoadStatus.Loading || paging.append is LoadStatus.Loading ->
            Box(modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
        else -> Spacer(modifier)
    }
}

@Composable
private fun CommentField(state: CommentsState, modifier: Modifier = Modifier) {
    Column(modifier) {
        state.replyingTo?.let { parent ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.comment_replying_to, handleLabel(parent.author.handle.value)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppIconButton(
                    AppIcon.Close,
                    stringResource(Res.string.comment_cancel_reply),
                    { state.eventSink(CommentEvent.CancelReply) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppTextField(
                state = state.draft,
                placeholder = stringResource(Res.string.detail_comment_placeholder),
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                AppIcon.Send,
                stringResource(Res.string.detail_send_comment),
                { state.eventSink(CommentEvent.Send) },
            )
        }
        state.error?.let { ErrorText(it) }
    }
}

private const val SENT_KEY = "sent/"

/** The rows of a thread: `thread/<uri>|root`, `|r|<reply>`, `|s|<sent reply>` and `|more`. AT-URIs never hold a `|`. */
private const val THREAD_KEY = "thread/"

/** The thread a row of it ([THREAD_KEY]) belongs to. */
private fun threadOf(key: String): String = key.removePrefix(THREAD_KEY).substringBefore('|')

/** How far replies are indented under their thread's top-level comment. */
private val REPLY_INDENT = 32.dp

/** The rows after the comments: the loading or retry row, and the field. */
private val TAIL_KEYS = listOf("comments/loading", "comments/field")

/** How many threads short of the last one listed the next page starts loading. */
private const val LOAD_MORE_AHEAD = 5

/**
 * The viewer replies to any listed comment that isn't hidden, deletes their own comments, hides or unhides others' on their own subject,
 * and mutes, blocks or reports the author of anyone else's. A placeholder and a just-sent comment never come here.
 */
private fun commentActions(comment: CommentView, state: CommentsState, reporting: Reporting): ImmutableSet<CommentAction> {
    val hidden = comment.hidden == true
    val manage =
        when {
            comment.author.did == state.viewerDid -> CommentAction.Delete
            !state.isOwn -> null
            hidden -> CommentAction.Unhide
            else -> CommentAction.Hide
        }
    val others = comment.author.did != state.viewerDid
    return listOfNotNull(
            CommentAction.Reply.takeIf { !hidden },
            manage,
            CommentAction.MuteAuthor.takeIf { others },
            CommentAction.BlockAuthor.takeIf { others },
            CommentAction.Report.takeIf { others && reporting.available },
        )
        .toImmutableSet()
}

/** How strongly a hidden comment shows, to the only two who see it. */
private const val HIDDEN_ALPHA = 0.55f
