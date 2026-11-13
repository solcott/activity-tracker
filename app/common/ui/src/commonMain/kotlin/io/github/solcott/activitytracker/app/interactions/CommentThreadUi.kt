package io.github.solcott.activitytracker.app.interactions

// The rows of a comment thread that aren't comments (CommentsUi).

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.ErrorRetryRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.comment_unavailable
import org.jetbrains.compose.resources.stringResource

/** In place of a top-level comment that isn't listed for the viewer, while some of its replies are. It says nothing more about it. */
@Composable
fun CommentUnavailable(modifier: Modifier = Modifier) {
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
fun MoreReplies(thread: CommentThread, eventSink: (CommentEvent) -> Unit, modifier: Modifier = Modifier) {
    val load = { eventSink(CommentEvent.LoadMoreReplies(thread.uri)) }
    when (val replies = thread.repliesState) {
        RepliesState.Loading -> Box(modifier.padding(8.dp)) { AppProgressIndicator(Modifier.size(20.dp)) }
        is RepliesState.Failed -> ErrorRetryRow(replies.error, onRetry = load, modifier)
        RepliesState.Idle ->
            Box(modifier) {
                AppTextButton(onClick = load) {
                    Text(
                        CommentsTexts.moreReplies(thread.moreReplies).string(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
    }
}
