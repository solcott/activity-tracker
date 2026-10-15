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
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** The "…" menu on one of the viewer's own comments: Delete, which asks first. */
@Composable
internal fun CommentMenu(comment: AtUri, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val delete = stringResource(Res.string.detail_delete)
    val items = remember(delete) { persistentListOf(AppMenuItem(delete, AppIcon.Delete, destructive = true) { confirming = true }) }
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
