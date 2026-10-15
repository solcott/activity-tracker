package io.github.solcott.activitytracker.app.detail

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
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.comment_hidden
import io.github.solcott.activitytracker.composeApp.resources.comment_hidden_by
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_count
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_placeholder
import io.github.solcott.activitytracker.composeApp.resources.detail_section_comments
import io.github.solcott.activitytracker.composeApp.resources.detail_send_comment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The detail screen's comments: lazy items, paged as the viewer scrolls (ActivityDetailUi).

/**
 * Asks for the next page of comments when the last one listed comes within [LOAD_MORE_AHEAD] items of being shown (the rows after the
 * comments count as the end), unless one is loading, the last failed or every page is in. The presenter drops a repeat while one loads.
 */
@Composable
internal fun LoadMoreNearTheEnd(listState: LazyListState, state: ActivityDetailState.Loaded) {
    val paging = state.comments
    val tail = state.sentComments.size + TAIL_KEYS.size
    val near by
        remember(listState, tail) {
            derivedStateOf {
                val info = listState.layoutInfo
                val lastShown = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                lastShown >= info.totalItemsCount - 1 - tail - LOAD_MORE_AHEAD
            }
        }
    val wanted = near && !paging.isLoading && !paging.endReached && paging.error == null
    SideEffect(wanted, paging.items.size) { if (wanted) state.eventSink(ActivityDetailEvent.LoadMoreComments) }
}

/**
 * The comment count (hidden ones left out), the comments loaded so far oldest first, each with a menu to delete the viewer's own or hide
 * others' on their own activity, the viewer's just sent, a row while more load or when they couldn't, then a field to add one and why the
 * last one couldn't be sent, deleted, hidden or unhidden. A hidden comment, listed only for the athlete and its author, is dimmed and says
 * so.
 */
internal fun LazyListScope.comments(state: ActivityDetailState.Loaded) {
    item(key = "comments") { CommentsHeader(state, Modifier.padding(horizontal = CARD_PADDING)) }
    items(state.comments.items, key = { COMMENT_KEY + it.uri.value }) { comment ->
        CommentRow(
            author = comment.author.displayName ?: comment.author.handle.value,
            text = comment.text,
            hidden = comment.hidden == true,
            actions = commentActions(comment, state),
            uri = comment.uri,
            state = state,
            modifier = Modifier.padding(horizontal = CARD_PADDING),
        )
    }
    items(state.sentComments, key = { SENT_KEY + it.uri.value }) { comment ->
        CommentRow(
            author = comment.author.displayName ?: comment.author.handle.value,
            text = comment.text,
            hidden = false,
            actions = persistentSetOf(CommentAction.Delete),
            uri = comment.uri,
            state = state,
            modifier = Modifier.padding(horizontal = CARD_PADDING),
        )
    }
    item(key = TAIL_KEYS[0]) { CommentsLoading(state, Modifier.padding(horizontal = CARD_PADDING)) }
    item(key = TAIL_KEYS[1]) { CommentField(state, Modifier.padding(horizontal = CARD_PADDING)) }
}

@Composable
private fun CommentsHeader(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.detail_section_comments), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        val count = state.activity.commentCount.toInt()
        Text(
            pluralStringResource(Res.plurals.detail_comment_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("LongParameterList") // A listed comment or a just-sent one, which share no type.
@Composable
private fun CommentRow(
    author: String,
    text: String,
    hidden: Boolean,
    actions: ImmutableSet<CommentAction>,
    uri: AtUri,
    state: ActivityDetailState.Loaded,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).alpha(if (hidden) HIDDEN_ALPHA else 1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(author, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (hidden) {
                val athlete = state.activity.author.let { it.displayName ?: it.handle.value }
                Text(
                    if (state.isOwn) stringResource(Res.string.comment_hidden) else stringResource(Res.string.comment_hidden_by, athlete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actions.isNotEmpty()) key(uri) { CommentMenu(uri, actions, state.eventSink) }
    }
}

/** A spinner while a page of comments loads, or why one couldn't and a way to try again; nothing otherwise. */
@Composable
private fun CommentsLoading(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val error = state.comments.error
    when {
        error != null ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(errorText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.RetryComments) }) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
        state.comments.isLoading ->
            Box(modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
        else -> Spacer(modifier)
    }
}

@Composable
private fun CommentField(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppTextField(
                state = state.draftComment,
                placeholder = stringResource(Res.string.detail_comment_placeholder),
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                AppIcon.Send,
                stringResource(Res.string.detail_send_comment),
                { state.eventSink(ActivityDetailEvent.SendComment) },
            )
        }
        state.commentError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
    }
}

private const val COMMENT_KEY = "comment/"
private const val SENT_KEY = "sent/"

/** The rows after the comments: the loading or retry row, and the field. */
private val TAIL_KEYS = listOf("comments/loading", "comments/field")

/** How many comments short of the last one listed the next page starts loading. */
private const val LOAD_MORE_AHEAD = 5

/** The viewer deletes their own comments, and hides or unhides others' on their own activity. */
private fun commentActions(comment: CommentView, state: ActivityDetailState.Loaded): ImmutableSet<CommentAction> =
    when {
        comment.author.did == state.viewerDid -> persistentSetOf(CommentAction.Delete)
        !state.isOwn -> persistentSetOf()
        comment.hidden == true -> persistentSetOf(CommentAction.Unhide)
        else -> persistentSetOf(CommentAction.Hide)
    }

/** How strongly a hidden comment shows, to the only two who see it. */
private const val HIDDEN_ALPHA = 0.55f
