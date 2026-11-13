package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The "…" menu on a comment by [author], with [actions] (at least one). */
@Composable
fun CommentMenu(
    comment: AtUri,
    author: String,
    actions: ImmutableSet<CommentAction>,
    eventSink: (CommentEvent) -> Unit,
    modifier: Modifier = Modifier,
    onReport: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<CommentAction?>(null) }
    val delete = CommentsTexts.label(CommentAction.Delete).string()
    val hide = CommentsTexts.label(CommentAction.Hide).string()
    val unhide = CommentsTexts.label(CommentAction.Unhide).string()
    val mute = CommentsTexts.label(CommentAction.MuteAuthor).string()
    val block = CommentsTexts.label(CommentAction.BlockAuthor).string()
    val report = CommentsTexts.label(CommentAction.Report).string()
    val items =
        remember(actions, delete, hide, unhide, mute, block, report, comment, onReport) {
            actions
                .sortedBy { it.ordinal }
                .mapNotNull { action ->
                    when (action) {
                        CommentAction.Reply -> null // A button under the comment.
                        CommentAction.Hide -> AppMenuItem(hide, AppIcon.Hide) { eventSink(CommentEvent.Hide(comment)) }
                        CommentAction.Unhide -> AppMenuItem(unhide, AppIcon.Unhide) { eventSink(CommentEvent.Unhide(comment)) }
                        CommentAction.Delete -> AppMenuItem(delete, AppIcon.Delete, destructive = true) { confirming = action }
                        CommentAction.MuteAuthor -> AppMenuItem(mute) { eventSink(CommentEvent.MuteAuthor(comment)) }
                        CommentAction.BlockAuthor -> AppMenuItem(block, destructive = true) { confirming = action }
                        CommentAction.Report -> AppMenuItem(report) { onReport() }
                    }
                }
                .toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.More, CommentsTexts.more.string(), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    val asking = confirming
    val confirmation = asking?.let { CommentsTexts.confirmation(it, AppText.written(author)) }
    if (asking != null && confirmation != null) {
        val event = if (asking == CommentAction.Delete) CommentEvent.Delete(comment) else CommentEvent.BlockAuthor(comment)
        AppAlertDialog(
            title = confirmation.title.string(),
            message = confirmation.message.string(),
            actions = persistentListOf(AppDialogAction(confirmation.confirm.string(), destructive = true) { eventSink(event) }),
            cancelLabel = CommentsTexts.cancel.string(),
            onDismiss = { confirming = null },
        )
    }
}
