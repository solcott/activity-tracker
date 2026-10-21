package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_block
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.block_commenter
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_message
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_title
import io.github.solcott.activitytracker.composeApp.resources.comment_more
import io.github.solcott.activitytracker.composeApp.resources.delete_comment_message
import io.github.solcott.activitytracker.composeApp.resources.delete_comment_title
import io.github.solcott.activitytracker.composeApp.resources.detail_delete
import io.github.solcott.activitytracker.composeApp.resources.hide_comment
import io.github.solcott.activitytracker.composeApp.resources.mute_commenter
import io.github.solcott.activitytracker.composeApp.resources.unhide_comment
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The "…" menu on a comment by [author], with [actions] (at least one). */
@Composable
internal fun CommentMenu(
    comment: AtUri,
    author: String,
    actions: ImmutableSet<CommentAction>,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var confirmingBlock by remember { mutableStateOf(false) }
    val delete = stringResource(Res.string.detail_delete)
    val hide = stringResource(Res.string.hide_comment)
    val unhide = stringResource(Res.string.unhide_comment)
    val mute = stringResource(Res.string.mute_commenter)
    val block = stringResource(Res.string.block_commenter)
    val items =
        remember(actions, delete, hide, unhide, mute, block, comment) {
            actions
                .sortedBy { it.ordinal }
                .mapNotNull { action ->
                    when (action) {
                        CommentAction.Reply -> null // A button under the comment.
                        CommentAction.Hide -> AppMenuItem(hide, AppIcon.Hide) { eventSink(ActivityDetailEvent.HideComment(comment)) }
                        CommentAction.Unhide ->
                            AppMenuItem(unhide, AppIcon.Unhide) { eventSink(ActivityDetailEvent.UnhideComment(comment)) }
                        CommentAction.Delete -> AppMenuItem(delete, AppIcon.Delete, destructive = true) { confirming = true }
                        CommentAction.MuteAuthor -> AppMenuItem(mute) { eventSink(ActivityDetailEvent.MuteCommenter(comment)) }
                        CommentAction.BlockAuthor -> AppMenuItem(block, destructive = true) { confirmingBlock = true }
                    }
                }
                .toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.More, stringResource(Res.string.comment_more), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    if (confirming) {
        AppAlertDialog(
            title = stringResource(Res.string.delete_comment_title),
            message = stringResource(Res.string.delete_comment_message),
            actions =
                persistentListOf(AppDialogAction(delete, destructive = true) { eventSink(ActivityDetailEvent.DeleteComment(comment)) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { confirming = false },
        )
    }
    if (confirmingBlock) {
        val confirm = stringResource(Res.string.action_block)
        AppAlertDialog(
            title = stringResource(Res.string.block_confirm_title, author),
            message = stringResource(Res.string.block_confirm_message),
            actions =
                persistentListOf(AppDialogAction(confirm, destructive = true) { eventSink(ActivityDetailEvent.BlockCommenter(comment)) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { confirmingBlock = false },
        )
    }
}
