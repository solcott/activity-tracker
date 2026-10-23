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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_component_actions
import io.github.solcott.activitytracker.composeApp.resources.gear_component_between
import io.github.solcott.activitytracker.composeApp.resources.gear_component_replace
import io.github.solcott.activitytracker.composeApp.resources.gear_component_since
import io.github.solcott.activitytracker.composeApp.resources.gear_component_unmount
import io.github.solcott.activitytracker.composeApp.resources.gear_components
import io.github.solcott.activitytracker.composeApp.resources.gear_components_mount
import io.github.solcott.activitytracker.composeApp.resources.gear_components_previous
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_new
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_none
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_pick_title
import io.github.solcott.activitytracker.composeApp.resources.gear_mount_replace_title
import io.github.solcott.activitytracker.composeApp.resources.gear_mounted_on
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearView
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

// The gear page's Components section, its mount history, and the owner's mount picker and unmount date (gear/gear-components).

/**
 * Components: what's mounted on the item now, each with its main total, then what was mounted before with its dates; the owner can unmount
 * or replace each one and mount another. Then what the item itself was mounted on, oldest first.
 */
internal fun LazyListScope.componentItems(state: GearState, page: GearPage, section: @Composable (String) -> Unit) {
    if (page.components.isNotEmpty() || page.previous.isNotEmpty() || page.isOwner) {
        item(key = "components-header") { section(stringResource(Res.string.gear_components)) }
        items(page.components, key = { "component/${it.gear.uri.value}" }) { component ->
            ComponentRow(
                component.gear,
                detail = gearMainTotalLabel(GearType.mainTotal(component.gear.type), component.totals),
                dates = stringResource(Res.string.gear_component_since, dateOf(component.from)),
                onClick = { state.eventSink(GearEvent.OpenGear(component.gear.uri.value)) },
                actions = if (page.isOwner) ({ ComponentMenu(component, enabled = !state.mountSaving, send = state.eventSink) }) else null,
            )
        }
        state.error
            ?.takeIf { it.kind in MOUNT_ERRORS }
            ?.let { error ->
                item(key = "components-error") {
                    Text(errorText(error), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        if (page.isOwner) {
            item(key = "components-mount") {
                AppTextButton(onClick = { state.eventSink(GearEvent.Mount) }, enabled = !state.mountSaving) {
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
                    detail = gearMainTotalLabel(GearType.mainTotal(component.gear.type), component.totals),
                    dates = datesOf(component.from, component.to),
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
                detail = null,
                dates = datesOf(mount.from, mount.to),
                onClick = { state.eventSink(GearEvent.OpenGear(mount.parent.uri.value)) },
            )
        }
    }
}

/** The owner's mount picker and unmount date, while either is open. */
@Composable
internal fun MountDialogs(state: GearState) {
    state.picker?.let { MountSheet(it, state.eventSink) }
    state.unmounting?.let { UnmountDatePicker(it, state.eventSink) }
}

@Composable
private fun ComponentRow(
    gear: GearView,
    detail: String?,
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
                listOfNotNull(gear.kind?.let { gearKindLabel(GearType.known(gear.type), it) } ?: gearTypeLabel(gear.type), detail)
                    .joinToString(" · "),
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
            stringResource(Res.string.gear_component_actions, component.gear.name),
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
            title =
                picker.replacing?.let { stringResource(Res.string.gear_mount_replace_title, it.gear.name) }
                    ?: stringResource(Res.string.gear_mount_pick_title),
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        candidate.kind?.let { gearKindLabel(GearType.known(candidate.type), it) } ?: gearTypeLabel(candidate.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The date to unmount [component] on: after the day it was mounted, today by default. */
@Composable
private fun UnmountDatePicker(component: GearComponent, send: (GearEvent) -> Unit) {
    val zone = LocalTimeZone.current
    val clock = LocalClock.current
    val today = remember { clock.now().toEpochMilliseconds() }
    val firstDay = remember(component) { component.from.toLocalDateTime(zone).date.toEpochDays() + 1 }
    val bounds = remember(firstDay) { DateBounds(minDateMillis = firstDay * MILLIS_PER_DAY) }
    AppDatePickerDialog(
        initialSelectedDateMillis = maxOf(today, firstDay * MILLIS_PER_DAY),
        selectableDates = bounds,
        onConfirm = { millis ->
            if (millis == null) send(GearEvent.DismissUnmount)
            else send(GearEvent.ConfirmUnmount(LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY).toInt())))
        },
        onDismiss = { send(GearEvent.DismissUnmount) },
    )
}

@Composable
@ReadOnlyComposable
private fun dateOf(instant: Instant): String = instant.toLocalDateTime(LocalTimeZone.current).date.toString()

@Composable
private fun datesOf(from: Instant, to: Instant?): String =
    if (to == null) stringResource(Res.string.gear_component_since, dateOf(from))
    else stringResource(Res.string.gear_component_between, dateOf(from), dateOf(to))

private const val MILLIS_PER_DAY = 86_400_000L

/** The errors a mount, unmount or replace shows by the Components section rather than above the activities. */
internal val MOUNT_ERRORS =
    setOf(
        UiError.Kind.SaveMount,
        UiError.Kind.MountOverlap,
        UiError.Kind.MountLoop,
        UiError.Kind.MountTooDeep,
        UiError.Kind.MountEndsBeforeStart,
    )
