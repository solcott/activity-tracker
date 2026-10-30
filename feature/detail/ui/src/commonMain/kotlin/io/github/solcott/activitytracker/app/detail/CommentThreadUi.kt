package io.github.solcott.activitytracker.app.detail

// The rows of a comment thread that aren't comments (CommentsUi).

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.comment_unavailable
import io.github.solcott.activitytracker.composeApp.resources.view_more_replies
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** In place of a top-level comment that isn't listed for the viewer, while some of its replies are. It says nothing more about it. */
@Composable
internal fun CommentUnavailable(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.comment_unavailable),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "View n more replies" under a thread, a spinner while they load, or why they couldn't and a way to try again. */
@Composable
internal fun MoreReplies(thread: CommentThread, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val load = { eventSink(ActivityDetailEvent.LoadMoreReplies(thread.uri)) }
    when (val replies = thread.repliesState) {
        RepliesState.Loading -> Box(modifier.padding(8.dp)) { AppProgressIndicator(Modifier.size(20.dp)) }
        is RepliesState.Failed ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(errorText(replies.error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = load) { Text(stringResource(Res.string.action_retry)) }
            }
        RepliesState.Idle ->
            Box(modifier) {
                AppTextButton(onClick = load) {
                    Text(
                        pluralStringResource(Res.plurals.view_more_replies, thread.moreReplies, thread.moreReplies),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
    }
}
