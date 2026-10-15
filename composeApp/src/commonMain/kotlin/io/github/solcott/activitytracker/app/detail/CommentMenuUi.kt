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
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.comment_more
import io.github.solcott.activitytracker.composeApp.resources.delete_comment_message
import io.github.solcott.activitytracker.composeApp.resources.delete_comment_title
import io.github.solcott.activitytracker.composeApp.resources.detail_delete
import io.github.solcott.activitytracker.composeApp.resources.hide_comment
import io.github.solcott.activitytracker.composeApp.resources.unhide_comment
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The "…" menu on a comment, with [actions] (at least one). */
@Composable
internal fun CommentMenu(
    comment: AtUri,
    actions: ImmutableSet<CommentAction>,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val delete = stringResource(Res.string.detail_delete)
    val hide = stringResource(Res.string.hide_comment)
    val unhide = stringResource(Res.string.unhide_comment)
    val items =
        remember(actions, delete, hide, unhide, comment) {
            actions
                .sortedBy { it.ordinal }
                .map { action ->
                    when (action) {
                        CommentAction.Hide -> AppMenuItem(hide, AppIcon.Hide) { eventSink(ActivityDetailEvent.HideComment(comment)) }
                        CommentAction.Unhide ->
                            AppMenuItem(unhide, AppIcon.Unhide) { eventSink(ActivityDetailEvent.UnhideComment(comment)) }
                        CommentAction.Delete -> AppMenuItem(delete, AppIcon.Delete, destructive = true) { confirming = true }
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
}
