package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The owner's "…" menu, as [ActivityPageTexts.ownerMenu] lists it: each item sends its event, but Split (confirmed), Export (its sheet) and
 * Delete, which asks first.
 */
@Composable
internal fun ManageMenu(page: ActivityDetailPage, modifier: Modifier = Modifier) {
    val manage = page.manage ?: return
    val export = page.export
    val eventSink = page.eventSink
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    // The file is built: the sheet's done, and the detail screen hands the file off.
    SideEffect(export?.file) { if (export?.file != null) exporting = false }
    val menu = ActivityPageTexts.ownerMenu(page)
    val labels = menu.map { it.label.string() }
    val items =
        menu
            .mapIndexed { i, item ->
                AppMenuItem(labels[i], item.action.icon, destructive = item.destructive) {
                    val event = item.event
                    when {
                        event != null -> eventSink(event)
                        item.action == ActivityMenuAction.Split -> splitting = true
                        item.action == ActivityMenuAction.Export -> exporting = true
                        else -> confirming = true
                    }
                }
            }
            .toImmutableList()
    Box(modifier) {
        AppIconButton(
            AppIcon.More,
            ActivityPageTexts.more.string(),
            onClick = { open = true },
            enabled = ActivityPageTexts.menuEnabled(manage),
        )
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    // A combined activity's members can go with it or stay, like a main activity's recordings.
    if (confirming) ConfirmDelete(manage, eventSink, onDismiss = { confirming = false })
    if (splitting) {
        AppAlertDialog(
            title = ActivityPageTexts.splitTitle.string(),
            message = ActivityPageTexts.splitMessage.string(),
            actions =
                persistentListOf(
                    AppDialogAction(ActivityPageTexts.split.string(), destructive = true) { eventSink(ActivityDetailEvent.Split) },
                ),
            cancelLabel = ActivityPageTexts.cancel.string(),
            onDismiss = { splitting = false },
        )
    }
    if (exporting && export != null) ExportSheet(export, eventSink, onDismiss = { exporting = false })
}

/** The icon beside each menu item. */
private val ActivityMenuAction.icon: AppIcon
    get() =
        when (this) {
            ActivityMenuAction.Edit -> AppIcon.ManualEntry
            ActivityMenuAction.Combine,
            ActivityMenuAction.CreateSegment -> AppIcon.Add
            ActivityMenuAction.AddRecording -> AppIcon.Import
            ActivityMenuAction.Split,
            ActivityMenuAction.Ungroup -> AppIcon.Ungroup
            ActivityMenuAction.HideStart,
            ActivityMenuAction.HideEnd -> AppIcon.Hide
            ActivityMenuAction.CreateDiveSite -> AppIcon.Location
            ActivityMenuAction.Export -> AppIcon.Export
            ActivityMenuAction.Delete -> AppIcon.Delete
        }

/**
 * "Delete this activity?" A main activity's other recordings can go with it, or stay as activities of their own: deleting it alone would
 * leave them pointing at nothing.
 */
@Composable
private fun ConfirmDelete(manage: ActivityManage, eventSink: (ActivityDetailEvent) -> Unit, onDismiss: () -> Unit) {
    val choices = ActivityPageTexts.deleteChoices(manage)
    val labels = choices.map { it.label.string() }
    AppAlertDialog(
        title = ActivityPageTexts.deleteTitle.string(),
        message = ActivityPageTexts.deleteMessage(manage).string(),
        actions =
            choices
                .mapIndexed { i, choice -> AppDialogAction(labels[i], destructive = true) { eventSink(choice.event) } }
                .toImmutableList(),
        cancelLabel = ActivityPageTexts.cancel.string(),
        onDismiss = onDismiss,
    )
}
