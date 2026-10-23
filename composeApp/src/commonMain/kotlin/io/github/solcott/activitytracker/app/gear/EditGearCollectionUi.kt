package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_add_items
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_add_items_done
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_edit_title
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_full
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_items
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_move_down
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_move_up
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_name
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_name_required
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_new
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_no_more_items
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_notes
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_remove
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_save
import io.github.solcott.activitytracker.composeApp.resources.gear_collections_help
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The collection form: its name and notes, then its items in order, each moved or removed in place, and Add items. */
@CircuitInject(EditGearCollectionScreen::class, SessionScope::class)
@Composable
fun EditGearCollectionUi(state: EditGearCollectionState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(if (state.isNew) Res.string.gear_collection_new else Res.string.gear_collection_edit_title),
                onBack = { state.eventSink(EditGearCollectionEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(EditGearCollectionEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadGearCollection, retry, contentPadding = padding) {
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, contentPadding = padding) {
                CollectionForm(state, Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp))
            }
        }
    }
}

@Composable
private fun CollectionForm(state: EditGearCollectionState, modifier: Modifier = Modifier) {
    val enabled = !state.saving
    val send = state.eventSink
    var adding by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            state.name,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_collection_name),
            enabled = enabled,
            isError = state.nameMissing,
            supportingText = if (state.nameMissing) stringResource(Res.string.gear_collection_name_required) else null,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation.maxLength(GearCollectionRecord.MAX_NAME),
        )
        AppTextField(
            state.notes,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_collection_notes),
            enabled = enabled,
            inputTransformation = InputTransformation.maxLength(GearCollectionRecord.MAX_NOTES),
        )
        Text(
            stringResource(Res.string.gear_collections_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(Res.string.gear_collection_items), style = MaterialTheme.typography.titleSmall)
        if (state.members.isEmpty()) {
            Text(stringResource(Res.string.gear_collection_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            state.members.forEachIndexed { index, row ->
                MemberRow(row, index, last = state.members.lastIndex, enabled = enabled, send = send)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (state.full) {
            Text(stringResource(Res.string.gear_collection_full), style = MaterialTheme.typography.bodySmall)
        } else {
            AppSecondaryButton(onClick = { adding = true }, enabled = enabled) {
                Text(stringResource(Res.string.gear_collection_add_items))
            }
        }
        state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        AppButton(onClick = { send(EditGearCollectionEvent.Save) }, Modifier.fillMaxWidth(), enabled = enabled) {
            Text(stringResource(Res.string.gear_collection_save))
        }
    }
    if (adding) {
        AddItemsSheet(
            state.others,
            onDismiss = { adding = false },
            onAdd = { rkeys ->
                adding = false
                send(EditGearCollectionEvent.Add(rkeys))
            },
        )
    }
}

/** A member with buttons to move it up or down and to remove it. */
@Composable
private fun MemberRow(row: GearRow, index: Int, last: Int, enabled: Boolean, send: (EditGearCollectionEvent) -> Unit) {
    GearRowItem(row, onOpen = null, showRetired = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                AppIcon.ChevronUp,
                stringResource(Res.string.gear_collection_move_up, row.name),
                { send(EditGearCollectionEvent.Move(index, index - 1)) },
                enabled = enabled && index > 0,
            )
            AppIconButton(
                AppIcon.ChevronDown,
                stringResource(Res.string.gear_collection_move_down, row.name),
                { send(EditGearCollectionEvent.Move(index, index + 1)) },
                enabled = enabled && index < last,
            )
            AppIconButton(
                AppIcon.Close,
                stringResource(Res.string.gear_collection_remove, row.name),
                { send(EditGearCollectionEvent.Remove(row.rkey)) },
                enabled = enabled,
            )
        }
    }
}

/** The athlete's other items to tick; Add adds the ticked ones in the order ticked. */
@Composable
private fun AddItemsSheet(others: ImmutableList<GearRow>, onDismiss: () -> Unit, onAdd: (ImmutableList<String>) -> Unit) {
    var ticked by remember { mutableStateOf(emptyList<String>()) }
    AppBottomSheet(onDismiss = onDismiss, sheetState = rememberAppSheetState()) {
        AppSheetHeader(
            title = stringResource(Res.string.gear_collection_add_items),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(Res.string.gear_collection_add_items_done),
            onConfirm = { onAdd(ticked.toImmutableList()) },
        )
        if (others.isEmpty()) {
            Text(
                stringResource(Res.string.gear_collection_no_more_items),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(others, key = { it.rkey }) { row ->
                val checked = row.rkey in ticked
                GearRowItem(
                    row,
                    onOpen = null,
                    modifier = Modifier.clickable(role = Role.Checkbox) { ticked = if (checked) ticked - row.rkey else ticked + row.rkey },
                    showRetired = true,
                ) {
                    AppCheckbox(checked, onCheckedChange = null)
                }
            }
        }
    }
}
