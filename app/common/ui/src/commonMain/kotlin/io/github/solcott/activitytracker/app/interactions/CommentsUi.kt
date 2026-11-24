package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ErrorRetryRow
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.ModerationNotice
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.shownNameLabel
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.comment_cancel_reply
import io.github.solcott.activitytracker.composeApp.resources.comment_reply
import io.github.solcott.activitytracker.composeApp.resources.comment_replying_to
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_placeholder
import io.github.solcott.activitytracker.composeApp.resources.detail_send_comment
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.uistate.LoadStatus
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.stringResource

// A subject's comments, an activity's or a post's (feed/comments): lazy items, threads paged as the viewer scrolls.

/** Asks for the next page of threads when the rows shown call for it (`CommentsTexts.wantsMore`). */
@Composable
fun LoadMoreNearTheEnd(listState: LazyListState, state: CommentsState) {
    val wanted by
        remember(listState, state) {
            derivedStateOf { CommentsTexts.wantsMore(listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }, state) }
        }
    SideEffect(wanted, state.paging.items.size) { if (wanted) state.eventSink(CommentEvent.LoadMore) }
}

/**
 * The comment count (hidden ones left out), then the threads loaded so far, oldest first: each top-level comment, or a placeholder when it
 * isn't listed, with its replies indented under it, the viewer's just-sent replies, and a row to view the rest. Each comment has Reply, and
 * a menu to delete the viewer's own or hide others' on their own subject. Rows are padded [horizontal] from the list's edges. Then the
 * viewer's just-sent comments, and a row while more load or when they couldn't. The field to add one is [CommentComposer], which the
 * Comments screen keeps at its foot. A hidden comment, listed only for the subject's author and its commenter, is dimmed and says so.
 * Report on others' comments opens [reporting]'s sheet.
 */
fun LazyListScope.comments(state: CommentsState, reporting: Reporting, horizontal: Dp = CARD_PADDING) {
    item(key = "comments") { CommentsHeader(state, Modifier.padding(horizontal = horizontal).padding(bottom = 8.dp)) }
    val top = Modifier.padding(horizontal = horizontal)
    val reply = Modifier.padding(start = horizontal + REPLY_INDENT, end = horizontal)
    for (thread in state.paging.items) {
        val prefix = CommentsTexts.threadKey(thread.uri)
        item(key = "$prefix|root") {
            val root = thread.root
            if (root == null) CommentUnavailable(top)
            else CommentRow(root, CommentsTexts.actions(root, state, reporting.available), state, reporting, top)
        }
        items(thread.replies, key = { "$prefix|r|${it.uri.value}" }) {
            CommentRow(it, CommentsTexts.actions(it, state, reporting.available), state, reporting, reply)
        }
        items(thread.sentReplies, key = { "$prefix|s|${it.uri.value}" }) { SentCommentRow(it, state, reply) }
        if (thread.moreReplies > 0 || thread.repliesState is RepliesState.Failed) {
            item(key = "$prefix|more") { MoreReplies(thread, state.eventSink, reply) }
        }
    }
    items(state.sent, key = { CommentsTexts.sentKey(it.uri) }) { SentCommentRow(it, state, top) }
    item(key = CommentsTexts.LOADING_KEY) { CommentsLoading(state, top) }
}

/** The count over the threads, as a list section's heading reads (`AppListSection`); the screen's bar already says they're comments. */
@Composable
private fun CommentsHeader(state: CommentsState, modifier: Modifier = Modifier) {
    Text(
        CommentsTexts.count(state).string(),
        modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
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
        createdAt = comment.createdAt,
        hidden = comment.hidden == true,
        actions = actions,
        uri = comment.uri,
        state = state,
        modifier = modifier,
        onReport = { reporting.eventSink(reporting.reportComment(comment, name)) },
        decision = ModerationDecision.of(comment.labels, comment.author.labels, isOwn = comment.author.did == state.viewerDid),
    )
}

/** The viewer's comment or reply the AppView doesn't list yet: they can delete it, but not reply to it until it's listed. */
@Composable
private fun SentCommentRow(comment: SentComment, state: CommentsState, modifier: Modifier = Modifier) =
    CommentRow(
        author = comment.author.shownName,
        text = comment.text,
        createdAt = comment.createdAt,
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
    createdAt: Instant,
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
            CommentAuthorLine(author, createdAt)
            ModerationNotice(decision, isOwn = false)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (hidden) {
                Text(
                    CommentsTexts.hiddenNote(state).string(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (CommentAction.Reply in actions) {
                // Pulled back by the button's own inset, so "Reply" starts in line with the comment's text.
                AppTextButton(onClick = { state.eventSink(CommentEvent.ReplyTo(uri)) }, Modifier.offset(x = -TEXT_BUTTON_INSET)) {
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

/**
 * The field to add a comment (feed/comments), at the Comments screen's foot: which comment it answers, with a way back to a top-level one,
 * the field and Send, and why the last send, delete, hide or block failed. [focusRequester] lets the screen put the cursor in it.
 */
@Composable
fun CommentComposer(state: CommentsState, modifier: Modifier = Modifier, focusRequester: FocusRequester = remember { FocusRequester() }) {
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
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
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

/** How far replies are indented under their thread's top-level comment. */
private val REPLY_INDENT = 32.dp

/** How strongly a hidden comment shows, to the only two who see it. */
private const val HIDDEN_ALPHA = 0.55f

/**
 * A comment's author and, after them, when it was written ([CommentsTexts.posted]: "5 minutes ago" today, "Yesterday at 6:12 PM" before),
 * on [LocalClock] in [LocalTimeZone]. The name gives way first when the line is short.
 */
@Composable
internal fun CommentAuthorLine(author: String, createdAt: Instant, modifier: Modifier = Modifier) {
    val posted = CommentsTexts.posted(createdAt, LocalClock.current.now(), LocalTimeZone.current).string()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            author,
            Modifier.weight(1f, fill = false).alignByBaseline(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            posted,
            Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A text button's own start padding (Material's 12dp), which a button lined up with text above it is pulled back by. */
private val TEXT_BUTTON_INSET = 12.dp
