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
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.delete_activity_all
import io.github.solcott.activitytracker.composeApp.resources.delete_activity_group_message
import io.github.solcott.activitytracker.composeApp.resources.delete_activity_message
import io.github.solcott.activitytracker.composeApp.resources.delete_activity_only_this
import io.github.solcott.activitytracker.composeApp.resources.delete_activity_title
import io.github.solcott.activitytracker.composeApp.resources.detail_delete
import io.github.solcott.activitytracker.composeApp.resources.detail_edit
import io.github.solcott.activitytracker.composeApp.resources.detail_export
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_end
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_start
import io.github.solcott.activitytracker.composeApp.resources.detail_more
import io.github.solcott.activitytracker.composeApp.resources.detail_ungroup
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The owner's "…" menu: Edit, Ungroup when the activity is grouped with just one other, Hide this start and end when its track can be read
 * ([canHideRouteEnds]), Export when it has data to export ([export]), and Delete, which asks first.
 */
@Composable
internal fun ManageMenu(
    manage: ActivityManage,
    canHideRouteEnds: Boolean,
    export: ActivityExportState?,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val canExport = export != null
    // The file is built: the sheet's done, and the detail screen hands the file off.
    SideEffect(export?.file) { if (export?.file != null) exporting = false }
    val ungroup = stringResource(Res.string.detail_ungroup)
    val delete = stringResource(Res.string.detail_delete)
    val edit = stringResource(Res.string.detail_edit)
    val hideStart = stringResource(Res.string.detail_hide_start)
    val hideEnd = stringResource(Res.string.detail_hide_end)
    val exportLabel = stringResource(Res.string.detail_export)
    val items =
        remember(manage.canUngroup, canHideRouteEnds, canExport, edit, ungroup, delete, hideStart, hideEnd, exportLabel, eventSink) {
            buildList {
                add(AppMenuItem(edit, AppIcon.ManualEntry) { eventSink(ActivityDetailEvent.Edit) })
                if (manage.canUngroup) add(AppMenuItem(ungroup, AppIcon.Ungroup) { eventSink(ActivityDetailEvent.Ungroup) })
                if (canHideRouteEnds) {
                    add(AppMenuItem(hideStart, AppIcon.Hide) { eventSink(ActivityDetailEvent.HideStart) })
                    add(AppMenuItem(hideEnd, AppIcon.Hide) { eventSink(ActivityDetailEvent.HideEnd) })
                }
                if (canExport) add(AppMenuItem(exportLabel, AppIcon.Export) { exporting = true })
                add(AppMenuItem(delete, AppIcon.Delete, destructive = true) { confirming = true })
            }
                .toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.More, stringResource(Res.string.detail_more), onClick = { open = true }, enabled = !manage.busy)
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    if (confirming) ConfirmDelete(manage.otherRecordings, eventSink, onDismiss = { confirming = false })
    if (exporting && export != null) ExportSheet(export, eventSink, onDismiss = { exporting = false })
}

/**
 * "Delete this activity?" A main activity's other recordings can go with it, or stay as activities of their own: deleting it alone would
 * leave them pointing at nothing.
 */
@Composable
private fun ConfirmDelete(otherRecordings: Int, eventSink: (ActivityDetailEvent) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(Res.string.delete_activity_title)
    val cancel = stringResource(Res.string.action_cancel)
    if (otherRecordings == 0) {
        val delete = stringResource(Res.string.detail_delete)
        AppAlertDialog(
            title = title,
            message = stringResource(Res.string.delete_activity_message),
            actions = persistentListOf(AppDialogAction(delete, destructive = true) { eventSink(ActivityDetailEvent.Delete(false)) }),
            cancelLabel = cancel,
            onDismiss = onDismiss,
        )
    } else {
        val all = pluralStringResource(Res.plurals.delete_activity_all, otherRecordings, otherRecordings + 1)
        val onlyThis = stringResource(Res.string.delete_activity_only_this)
        AppAlertDialog(
            title = title,
            message = pluralStringResource(Res.plurals.delete_activity_group_message, otherRecordings, otherRecordings),
            actions =
                persistentListOf(
                    AppDialogAction(all, destructive = true) { eventSink(ActivityDetailEvent.Delete(withRecordings = true)) },
                    AppDialogAction(onlyThis, destructive = true) { eventSink(ActivityDetailEvent.Delete(withRecordings = false)) },
                ),
            cancelLabel = cancel,
            onDismiss = onDismiss,
        )
    }
}
