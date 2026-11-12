package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The devices an import names that aren't linked to gear yet, a card each, and the offer to link the activity profile. */
@Composable
fun NewDevicesForm(fields: NewDevicesFields, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (fields.cards.isEmpty() && fields.profileOffer == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fields.cards.isNotEmpty()) {
            Text(ImportActivityTexts.newDevicesTitle.string(), style = MaterialTheme.typography.titleSmall)
            Text(
                ImportActivityTexts.newDevicesHelp.string(),
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
            Text(ImportActivityTexts.deviceName(card).string(), style = MaterialTheme.typography.titleSmall)
            ImportActivityTexts.deviceAbout(card)?.let { about ->
                Text(
                    about.string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val decisions = ImportActivityTexts.deviceDecisions.map { it.decision }
            AppSegmentedControl(
                labels = decisions.map { ImportActivityTexts.decision(it).string() }.toImmutableList(),
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
            label = ImportActivityTexts.deviceNameLabel.string(),
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        MenuField(
            label = ImportActivityTexts.deviceTypeLabel.string(),
            value = gearTypeLabel(card.type),
            items =
                (ImportActivityTexts.gearTypes.map { type ->
                        AppMenuItem(gearTypeLabel(type), checked = type == card.type) { send(NewDeviceEvent.TypeChanged(card.id, type)) }
                    })
                    .toImmutableList(),
            enabled = enabled,
        )
        val kinds = ImportActivityTexts.gearKinds(card.type)
        if (kinds.isNotEmpty()) {
            MenuField(
                label = ImportActivityTexts.deviceKindLabel.string(),
                value = ImportActivityTexts.deviceKind(card).string(),
                items =
                    (listOf(
                            AppMenuItem(ImportActivityTexts.deviceKindNone.string(), checked = card.kind == null) {
                                send(NewDeviceEvent.KindChanged(card.id, null))
                            },
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
                label = ImportActivityTexts.deviceMountLabel.string(),
                value = ImportActivityTexts.deviceMount(card, fields).string(),
                items =
                    (listOf(
                            AppMenuItem(ImportActivityTexts.deviceMountNone.string(), checked = card.mountOn == null) {
                                send(NewDeviceEvent.MountOn(card.id, null))
                            },
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
            Text(ImportActivityTexts.deviceCollectionsLabel.string(), style = MaterialTheme.typography.labelLarge)
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
            Text(ImportActivityTexts.profileLinkTitle(offer).string(), style = MaterialTheme.typography.titleSmall)
            Text(
                ImportActivityTexts.profileLinkHelp.string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MenuField(
                label = null,
                value = ImportActivityTexts.profileLinked(offer).string(),
                items =
                    (listOf(
                            AppMenuItem(ImportActivityTexts.profileLinkNone.string(), checked = offer.chosen == null) {
                                send(NewDeviceEvent.LinkProfile(null))
                            },
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
            ImportActivityTexts.gearConflict(conflict).string(),
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The import is saved: that its links weren't, and the offer to add the new gear to earlier imports, before leaving. */
@Composable
internal fun AfterImportPanel(after: AfterImport, send: (ImportActivityEvent) -> Unit, padding: PaddingValues) {
    ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ImportActivityTexts.afterTitle.string(), style = MaterialTheme.typography.titleMedium)
        if (after.linksNotSaved) Text(ImportActivityTexts.afterLinksNotSaved.string(), color = MaterialTheme.colorScheme.error)
        val past = after.pastImports
        if (past != null) {
            Text(ImportActivityTexts.pastOffer(past).string())
            if (past.applying) {
                AppProgressIndicator()
                Text(
                    ImportActivityTexts.pastProgress(past).string(),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                AppButton(onClick = { send(ImportActivityEvent.After.ApplyToPast) }, Modifier.fillMaxWidth()) {
                    Text(ImportActivityTexts.pastApply.string())
                }
                AppTextButton(onClick = { send(ImportActivityEvent.After.Done) }, Modifier.fillMaxWidth()) {
                    Text(ImportActivityTexts.pastSkip.string())
                }
            }
        } else {
            AppButton(onClick = { send(ImportActivityEvent.After.Done) }, Modifier.fillMaxWidth()) {
                Text(ImportActivityTexts.afterDone.string())
            }
        }
    }
}
