package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.gear.deviceName
import io.github.solcott.activitytracker.app.gear.gearKindLabel
import io.github.solcott.activitytracker.app.gear.gearTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.after_import_done
import io.github.solcott.activitytracker.composeApp.resources.after_import_links_not_saved
import io.github.solcott.activitytracker.composeApp.resources.after_import_title
import io.github.solcott.activitytracker.composeApp.resources.gear_conflict
import io.github.solcott.activitytracker.composeApp.resources.new_device_add
import io.github.solcott.activitytracker.composeApp.resources.new_device_collections
import io.github.solcott.activitytracker.composeApp.resources.new_device_kind
import io.github.solcott.activitytracker.composeApp.resources.new_device_kind_none
import io.github.solcott.activitytracker.composeApp.resources.new_device_later
import io.github.solcott.activitytracker.composeApp.resources.new_device_mount_none
import io.github.solcott.activitytracker.composeApp.resources.new_device_mount_on
import io.github.solcott.activitytracker.composeApp.resources.new_device_name
import io.github.solcott.activitytracker.composeApp.resources.new_device_not_gear
import io.github.solcott.activitytracker.composeApp.resources.new_device_recorder
import io.github.solcott.activitytracker.composeApp.resources.new_device_serial
import io.github.solcott.activitytracker.composeApp.resources.new_device_type
import io.github.solcott.activitytracker.composeApp.resources.new_device_weak
import io.github.solcott.activitytracker.composeApp.resources.new_devices_help
import io.github.solcott.activitytracker.composeApp.resources.new_devices_title
import io.github.solcott.activitytracker.composeApp.resources.past_imports_apply
import io.github.solcott.activitytracker.composeApp.resources.past_imports_offer
import io.github.solcott.activitytracker.composeApp.resources.past_imports_progress
import io.github.solcott.activitytracker.composeApp.resources.past_imports_skip
import io.github.solcott.activitytracker.composeApp.resources.profile_link_help
import io.github.solcott.activitytracker.composeApp.resources.profile_link_none
import io.github.solcott.activitytracker.composeApp.resources.profile_link_title
import io.github.solcott.activitytracker.lexicon.app.GearType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The devices an import names that aren't linked to gear yet, a card each, and the offer to link the activity profile. */
@Composable
fun NewDevicesForm(fields: NewDevicesFields, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (fields.cards.isEmpty() && fields.profileOffer == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fields.cards.isNotEmpty()) {
            Text(stringResource(Res.string.new_devices_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.new_devices_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fields.cards.forEach { card -> NewDeviceCardUi(card, fields, enabled) }
        }
        fields.profileOffer?.let { ProfileLink(it, fields.eventSink, enabled) }
    }
}

@Composable
private fun NewDeviceCardUi(card: NewDeviceCard, fields: NewDevicesFields, enabled: Boolean) {
    val send = fields.eventSink
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(deviceName(card.manufacturer, card.model), style = MaterialTheme.typography.titleSmall)
            val about =
                listOfNotNull(
                    stringResource(Res.string.new_device_recorder).takeIf { card.recorder },
                    card.serialTail?.let { stringResource(Res.string.new_device_serial, it) },
                    stringResource(Res.string.new_device_weak).takeIf { card.weak },
                )
            if (about.isNotEmpty()) {
                Text(
                    about.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val decisions = NewDeviceDecision.entries
            AppSegmentedControl(
                labels =
                    decisions
                        .map {
                            when (it) {
                                NewDeviceDecision.Later -> stringResource(Res.string.new_device_later)
                                NewDeviceDecision.Add -> stringResource(Res.string.new_device_add)
                                NewDeviceDecision.NotGear -> stringResource(Res.string.new_device_not_gear)
                            }
                        }
                        .toImmutableList(),
                selected = decisions.indexOf(card.decision),
                onSelect = { send(NewDeviceEvent.Decide(card.id, decisions[it])) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            if (card.decision == NewDeviceDecision.Add) NewItemFields(card, fields, enabled)
        }
    }
}

/** The new item's name, type and kind, what it's mounted on and its collections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewItemFields(card: NewDeviceCard, fields: NewDevicesFields, enabled: Boolean) {
    val send = fields.eventSink
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
            card.name,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.new_device_name),
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        MenuField(
            label = stringResource(Res.string.new_device_type),
            value = gearTypeLabel(card.type),
            items =
                (GearType.all.map { type ->
                        AppMenuItem(gearTypeLabel(type), checked = type == card.type) { send(NewDeviceEvent.TypeChanged(card.id, type)) }
                    })
                    .toImmutableList(),
            enabled = enabled,
        )
        val kinds = GearType.kinds(card.type)
        if (kinds.isNotEmpty()) {
            MenuField(
                label = stringResource(Res.string.new_device_kind),
                value = card.kind?.let { gearKindLabel(card.type, it) } ?: stringResource(Res.string.new_device_kind_none),
                items =
                    (listOf(
                            AppMenuItem(stringResource(Res.string.new_device_kind_none), checked = card.kind == null) {
                                send(NewDeviceEvent.KindChanged(card.id, null))
                            }
                        ) +
                            kinds.map { kind ->
                                AppMenuItem(gearKindLabel(card.type, kind), checked = kind == card.kind) {
                                    send(NewDeviceEvent.KindChanged(card.id, kind))
                                }
                            })
                        .toImmutableList(),
                enabled = enabled,
            )
        }
        if (fields.mountOptions.isNotEmpty()) {
            MenuField(
                label = stringResource(Res.string.new_device_mount_on),
                value =
                    fields.mountOptions.firstOrNull { it.uri == card.mountOn }?.name ?: stringResource(Res.string.new_device_mount_none),
                items =
                    (listOf(
                            AppMenuItem(stringResource(Res.string.new_device_mount_none), checked = card.mountOn == null) {
                                send(NewDeviceEvent.MountOn(card.id, null))
                            }
                        ) +
                            fields.mountOptions.map { option ->
                                AppMenuItem(option.name, checked = option.uri == card.mountOn) {
                                    send(NewDeviceEvent.MountOn(card.id, option.uri))
                                }
                            })
                        .toImmutableList(),
                enabled = enabled,
            )
        }
        if (fields.collections.isNotEmpty()) {
            Text(stringResource(Res.string.new_device_collections), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fields.collections.forEach { collection ->
                    AppFilterChip(
                        selected = collection.rkey in card.collections,
                        onClick = { send(NewDeviceEvent.ToggleCollection(card.id, collection.rkey)) },
                        label = { Text(collection.name) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/** Linking the activity profile to one of the picked items, or not. */
@Composable
private fun ProfileLink(offer: ProfileLinkOffer, send: (NewDeviceEvent) -> Unit, enabled: Boolean) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.profile_link_title, offer.name), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.profile_link_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MenuField(
                label = null,
                value = offer.options.firstOrNull { it.uri == offer.chosen }?.name ?: stringResource(Res.string.profile_link_none),
                items =
                    (listOf(
                            AppMenuItem(stringResource(Res.string.profile_link_none), checked = offer.chosen == null) {
                                send(NewDeviceEvent.LinkProfile(null))
                            }
                        ) +
                            offer.options.map { option ->
                                AppMenuItem(option.name, checked = option.uri == offer.chosen) {
                                    send(NewDeviceEvent.LinkProfile(option.uri))
                                }
                            })
                        .toImmutableList(),
                enabled = enabled,
            )
        }
    }
}

/** A labelled value that opens a menu of [items]. */
@Composable
private fun MenuField(label: String?, value: String, items: ImmutableList<AppMenuItem>, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        Box {
            AppSecondaryButton(onClick = { open = true }, enabled = enabled) { Text(value) }
            AppMenu(expanded = open, onDismiss = { open = false }, items = items)
        }
    }
}

/** Sensors matched more than one selected item of a type, which an activity usually has one of. */
@Composable
internal fun GearConflicts(conflicts: ImmutableList<GearConflict>, modifier: Modifier = Modifier) {
    conflicts.forEach { conflict ->
        Text(
            stringResource(Res.string.gear_conflict, gearTypeLabel(conflict.type), conflict.names.joinToString(", ")),
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The import is saved: that its links weren't, and the offer to add the new gear to earlier imports, before leaving. */
@Composable
internal fun AfterImportPanel(after: AfterImport, send: (ImportActivityEvent) -> Unit, padding: PaddingValues) {
    val scroll = rememberScrollState()
    ScrollbarBox(scroll, contentPadding = padding) {
        Column(
            Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.after_import_title), style = MaterialTheme.typography.titleMedium)
            if (after.linksNotSaved) Text(stringResource(Res.string.after_import_links_not_saved), color = MaterialTheme.colorScheme.error)
            val past = after.pastImports
            if (past != null) {
                Text(pluralStringResource(Res.plurals.past_imports_offer, past.count, past.count, past.gear.joinToString(", ")))
                if (past.applying) {
                    AppProgressIndicator()
                    Text(
                        stringResource(Res.string.past_imports_progress, past.done, past.count),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    AppButton(onClick = { send(ImportActivityEvent.After.ApplyToPast) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.past_imports_apply))
                    }
                    AppTextButton(onClick = { send(ImportActivityEvent.After.Done) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.past_imports_skip))
                    }
                }
            } else {
                AppButton(onClick = { send(ImportActivityEvent.After.Done) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.after_import_done))
                }
            }
        }
    }
}
