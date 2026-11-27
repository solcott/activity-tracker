package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.report.ReportTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Asks before blocking [author] from a trail report or photo's menu, as blocking from a profile or comment does. */
@Composable
internal fun TrailBlockConfirm(author: String, onBlock: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        title = AppText.named("block_confirm_title", AppText.written(author)).string(),
        message = ReportTexts.blockConfirmMessage.string(),
        actions = persistentListOf(AppDialogAction(ReportTexts.block.string(), destructive = true, onClick = onBlock)),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss,
    )
}
