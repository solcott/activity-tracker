package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.gear.gearKindLabel
import io.github.solcott.activitytracker.app.gear.gearTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.rememberReadOnlyTextFieldState
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_collection_count
import io.github.solcott.activitytracker.composeApp.resources.gear_field_add_collection
import io.github.solcott.activitytracker.composeApp.resources.gear_field_choose
import io.github.solcott.activitytracker.composeApp.resources.gear_field_clear
import io.github.solcott.activitytracker.composeApp.resources.gear_field_done
import io.github.solcott.activitytracker.composeApp.resources.gear_field_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_field_none
import io.github.solcott.activitytracker.composeApp.resources.gear_field_not_added
import io.github.solcott.activitytracker.composeApp.resources.gear_field_retired
import io.github.solcott.activitytracker.composeApp.resources.gear_field_title
import io.github.solcott.activitytracker.composeApp.resources.page_gear_item_value
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The form's gear: a field naming the items chosen, which opens a sheet of the athlete's items to tick, each with its kind and who may see
 * it. Picks go to the field's sink.
 */
@Composable
fun GearPicker(field: GearField, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val names = field.selected.mapNotNull { uri -> field.options.firstOrNull { it.uri == uri }?.name }
    val shown = if (names.isEmpty()) stringResource(Res.string.gear_field_none) else names.joinToString(", ")
    Box(modifier.fillMaxWidth()) {
        AppTextField(
            rememberReadOnlyTextFieldState(shown),
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_field_title),
            enabled = enabled,
            readOnly = true,
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        // A read-only field doesn't report clicks, so a transparent layer on top opens the sheet.
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.Button) { choosing = true })
    }
    if (choosing) GearSheet(field, onDismiss = { choosing = false })
}

@Composable
private fun GearSheet(field: GearField, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss = onDismiss, sheetState = rememberAppSheetState()) {
        AppSheetHeader(
            title = stringResource(Res.string.gear_field_choose),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(Res.string.gear_field_done),
            onConfirm = onDismiss,
        )
        if (field.options.isEmpty()) {
            Text(
                stringResource(Res.string.gear_field_empty),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppBottomSheet
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            if (field.collections.isNotEmpty()) CollectionMenu(field)
            if (field.selected.isNotEmpty()) {
                AppTextButton(onClick = { field.eventSink(GearEvent.Clear) }) { Text(stringResource(Res.string.gear_field_clear)) }
            }
        }
        if (field.notAdded > 0) {
            Text(
                pluralStringResource(Res.plurals.gear_field_not_added, field.notAdded, field.notAdded),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(field.options, key = { it.uri.value }) { option ->
                GearRow(option, checked = option.uri in field.selected) { field.eventSink(GearEvent.Toggle(option.uri)) }
            }
        }
    }
}

/** Add collection: a menu of the athlete's collections, each adding its items to the activity's gear. */
@Composable
private fun CollectionMenu(field: GearField) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppTextButton(onClick = { open = true }) { Text(stringResource(Res.string.gear_field_add_collection)) }
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            items =
                field.collections
                    .map { collection ->
                        val count = pluralStringResource(Res.plurals.gear_collection_count, collection.count, collection.count)
                        AppMenuItem(stringResource(Res.string.page_gear_item_value, collection.name, count)) {
                            field.eventSink(GearEvent.AddCollection(collection.rkey))
                        }
                    }
                    .toImmutableList(),
        )
    }
}

@Composable
private fun GearRow(option: GearOption, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCheckbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge)
            val what = option.kind?.let { gearKindLabel(option.type, it) } ?: gearTypeLabel(option.type)
            val detail = stringResource(Res.string.page_gear_item_value, what, visibilityLabel(option.visibility))
            Text(
                if (option.retired) stringResource(Res.string.page_gear_item_value, detail, stringResource(Res.string.gear_field_retired))
                else detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
