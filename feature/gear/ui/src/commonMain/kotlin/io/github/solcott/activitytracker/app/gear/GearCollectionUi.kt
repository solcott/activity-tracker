package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete_message
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete_title
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_edit
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_empty
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** A gear collection: its notes, then its members in order, retired ones marked. Edit and Delete are in the bar. */
@CircuitInject(GearCollectionScreen::class, SessionScope::class)
@Composable
fun GearCollectionUi(state: GearCollectionState, modifier: Modifier = Modifier) {
    val enabled = state.actionsEnabled
    val retry = { state.eventSink(GearCollectionEvent.Retry) }
    ListScreen(
        title = state.title.string(),
        onBack = { state.eventSink(GearCollectionEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadGearCollection,
        onRetry = retry,
        modifier = modifier,
        edgeToEdge = true,
        actions = {
            if (state.showsActions) {
                AppIconButton(
                    AppIcon.ManualEntry,
                    stringResource(Res.string.gear_collection_edit),
                    { state.eventSink(GearCollectionEvent.Edit) },
                    enabled = enabled,
                )
                AppIconButton(
                    AppIcon.Delete,
                    stringResource(Res.string.gear_collection_delete),
                    { state.eventSink(GearCollectionEvent.Delete) },
                    enabled = enabled,
                )
            }
        },
    ) { detail ->
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadGearCollection, onRetry = retry) }
        state.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.padding(16.dp)) } }
        detail.notes?.let { notes ->
            item(key = "notes") { Text(notes, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        if (detail.members.isEmpty()) {
            item(key = "empty") {
                EmptyListText(stringResource(Res.string.gear_collection_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(detail.members, key = { _, row -> row.rkey }) { _, row ->
            GearRowItem(row, onOpen = { state.eventSink(GearCollectionEvent.Open(row.rkey)) }, showRetired = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (state.confirmingDelete) {
        AppAlertDialog(
            title = stringResource(Res.string.gear_collection_delete_title),
            message = stringResource(Res.string.gear_collection_delete_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.gear_collection_delete), destructive = true) {
                        state.eventSink(GearCollectionEvent.ConfirmDelete)
                    },
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(GearCollectionEvent.DismissDelete) },
        )
    }
}
