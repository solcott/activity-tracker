package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.DateBounds
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.pickedDate
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.toPickerMillis
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_component_replace
import io.github.solcott.activitytracker.composeApp.resources.gear_component_unmount
import io.github.solcott.activitytracker.composeApp.resources.gear_components
import io.github.solcott.activitytracker.composeApp.resources.gear_components_mount
import io.github.solcott.activitytracker.composeApp.resources.gear_components_previous
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_new
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_none
import io.github.solcott.activitytracker.composeApp.resources.gear_mounted_on
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearView
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

// The gear page's Components section, its mount history, and the owner's mount picker and unmount date (gear/gear-components).

/**
 * Components: what's mounted on the item now, each with its main total, then what was mounted before with its dates; the owner can unmount
 * or replace each one and mount another. Then what the item itself was mounted on, oldest first.
 */
internal fun LazyListScope.componentItems(state: GearState, page: GearPage, section: @Composable (String) -> Unit) {
    if (state.showsComponents) {
        item(key = "components-header") { section(stringResource(Res.string.gear_components)) }
        items(page.components, key = { "component/${it.gear.uri.value}" }) { component ->
            ComponentRow(
                component.gear,
                line = GearPageTexts.componentLine(component, LocalUnitSystem.current).string(),
                dates = GearPageTexts.componentDates(component, LocalTimeZone.current).string(),
                onClick = { state.eventSink(GearEvent.OpenGear(component.gear.uri.value)) },
                actions =
                    if (state.managesComponents) ({ ComponentMenu(component, enabled = state.mountEnabled, send = state.eventSink) })
                    else null,
            )
        }
        state.componentsError?.let { error -> item(key = "components-error") { ErrorText(error, Modifier.padding(vertical = 8.dp)) } }
        if (state.managesComponents) {
            item(key = "components-mount") {
                AppTextButton(onClick = { state.eventSink(GearEvent.Mount) }, enabled = state.mountEnabled) {
                    Text(stringResource(Res.string.gear_components_mount))
                }
            }
        }
        if (page.previous.isNotEmpty()) {
            item(key = "components-previous") {
                Text(
                    stringResource(Res.string.gear_components_previous),
                    Modifier.padding(top = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(page.previous, key = { "previous/${it.gear.uri.value}/${it.from}" }) { component ->
                ComponentRow(
                    component.gear,
                    line = GearPageTexts.componentLine(component, LocalUnitSystem.current).string(),
                    dates = GearPageTexts.componentDates(component, LocalTimeZone.current).string(),
                    onClick = { state.eventSink(GearEvent.OpenGear(component.gear.uri.value)) },
                )
            }
        }
    }
    if (page.mounts.isNotEmpty()) {
        item(key = "mounts-header") { section(stringResource(Res.string.gear_mounted_on)) }
        items(page.mounts, key = { "mount/${it.parent.uri.value}/${it.from}" }) { mount ->
            ComponentRow(
                mount.parent,
                line = GearPageTexts.kindLine(mount.parent).string(),
                dates = GearPageTexts.mountDates(mount, LocalTimeZone.current).string(),
                onClick = { state.eventSink(GearEvent.OpenGear(mount.parent.uri.value)) },
            )
        }
    }
}

/** The owner's mount picker and unmount date, while either is open. */
@Composable
internal fun MountDialogs(state: GearState) {
    state.picker?.let { MountSheet(it, state.eventSink) }
    if (state.unmounting != null) UnmountDatePicker(state)
}

@Composable
private fun ComponentRow(
    gear: GearView,
    line: String,
    dates: String,
    onClick: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(gear.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions?.invoke()
    }
}

/** Unmount and Replace for a component mounted now. */
@Composable
private fun ComponentMenu(component: GearComponent, enabled: Boolean, send: (GearEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val uri = component.gear.uri.value
    val unmount = stringResource(Res.string.gear_component_unmount)
    val replace = stringResource(Res.string.gear_component_replace)
    Box {
        AppIconButton(
            AppIcon.More,
            GearPageTexts.componentActions(component).string(),
            { open = true },
            enabled = enabled,
        )
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            items =
                persistentListOf(
                    AppMenuItem(replace) {
                        open = false
                        send(GearEvent.Replace(uri))
                    },
                    AppMenuItem(unmount) {
                        open = false
                        send(GearEvent.Unmount(uri))
                    },
                ),
        )
    }
}

/** The owner's items that can be mounted here, to tap one; New item adds one first. */
@Composable
private fun MountSheet(picker: GearMountPicker, send: (GearEvent) -> Unit) {
    val dismiss = { send(GearEvent.DismissMount) }
    AppBottomSheet(onDismiss = dismiss, sheetState = rememberAppSheetState()) {
        AppSheetHeader(
            title = GearPageTexts.pickerTitle(picker).string(),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = dismiss,
            confirmLabel = stringResource(Res.string.gear_mount_new),
            onConfirm = { send(GearEvent.MountNew) },
        )
        if (picker.candidates.isEmpty()) {
            Text(
                stringResource(Res.string.gear_mount_none),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(picker.candidates, key = { it.rkey }) { candidate ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { send(GearEvent.ChooseMount(candidate.rkey)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        GearPageTexts.candidateLine(candidate).string(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The date to unmount the component on: from the day after it was mounted, today by default ([GearState.unmountFirstDay]). */
@Composable
private fun UnmountDatePicker(state: GearState) {
    val zone = LocalTimeZone.current
    val clock = LocalClock.current
    val firstDay = remember(state.unmounting, zone) { state.unmountFirstDay(zone) } ?: return
    val initialDay = remember(state.unmounting, zone) { state.unmountInitialDay(clock.now(), zone) } ?: firstDay
    val firstMillis = firstDay.toPickerMillis()
    val bounds = remember(firstMillis) { DateBounds(minDateMillis = firstMillis) }
    AppDatePickerDialog(
        initialSelectedDateMillis = initialDay.toPickerMillis(),
        selectableDates = bounds,
        onConfirm = { millis ->
            if (millis == null) state.eventSink(GearEvent.DismissUnmount) else state.eventSink(GearEvent.ConfirmUnmount(pickedDate(millis)))
        },
        onDismiss = { state.eventSink(GearEvent.DismissUnmount) },
    )
}
