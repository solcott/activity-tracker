package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete_message
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_delete_title
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_edit
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_collections
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** A gear collection: its notes, then its members in order, retired ones marked. Edit and Delete are in the bar. */
@CircuitInject(GearCollectionScreen::class, SessionScope::class)
@Composable
fun GearCollectionUi(state: GearCollectionState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val enabled = !state.deleting
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = state.content.data.name.ifEmpty { stringResource(Res.string.gear_collections) },
                onBack = { state.eventSink(GearCollectionEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.content.data.name.isNotEmpty()) {
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
            )
        },
    ) { padding ->
        val retry = { state.eventSink(GearCollectionEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadGearCollection, retry, contentPadding = padding) { detail ->
            val listState = rememberLazyListState()
            ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                    item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadGearCollection, onRetry = retry) }
                    state.error?.let { error ->
                        item(key = "error") { Text(errorText(error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    }
                    detail.notes?.let { notes ->
                        item(key = "notes") {
                            Text(notes, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (detail.members.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(Res.string.gear_collection_empty),
                                Modifier.fillMaxWidth().padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(detail.members, key = { _, row -> row.rkey }) { _, row ->
                        GearRowItem(row, onOpen = { state.eventSink(GearCollectionEvent.Open(row.rkey)) }, showRetired = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
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
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(GearCollectionEvent.DismissDelete) },
        )
    }
}
