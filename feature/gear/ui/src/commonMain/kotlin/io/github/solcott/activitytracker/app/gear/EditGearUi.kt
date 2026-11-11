package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.sport.SportTypeGroups
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.PastDateField
import io.github.solcott.activitytracker.app.ui.TapField
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.compressPhoto
import io.github.solcott.activitytracker.app.ui.pickedDate
import io.github.solcott.activitytracker.app.ui.rememberExpandedSports
import io.github.solcott.activitytracker.app.ui.sportTypeList
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.toPickerMillis
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired_choose
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired_clear
import io.github.solcott.activitytracker.composeApp.resources.gear_brand
import io.github.solcott.activitytracker.composeApp.resources.gear_default_for
import io.github.solcott.activitytracker.composeApp.resources.gear_default_for_hint
import io.github.solcott.activitytracker.composeApp.resources.gear_delete
import io.github.solcott.activitytracker.composeApp.resources.gear_delete_message
import io.github.solcott.activitytracker.composeApp.resources.gear_delete_title
import io.github.solcott.activitytracker.composeApp.resources.gear_kind
import io.github.solcott.activitytracker.composeApp.resources.gear_model
import io.github.solcott.activitytracker.composeApp.resources.gear_name
import io.github.solcott.activitytracker.composeApp.resources.gear_name_required
import io.github.solcott.activitytracker.composeApp.resources.gear_notes
import io.github.solcott.activitytracker.composeApp.resources.gear_photo
import io.github.solcott.activitytracker.composeApp.resources.gear_photo_remove
import io.github.solcott.activitytracker.composeApp.resources.gear_retire
import io.github.solcott.activitytracker.composeApp.resources.gear_save
import io.github.solcott.activitytracker.composeApp.resources.gear_sports
import io.github.solcott.activitytracker.composeApp.resources.gear_sports_choose
import io.github.solcott.activitytracker.composeApp.resources.gear_sports_done
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_help
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_time
import io.github.solcott.activitytracker.composeApp.resources.gear_type
import io.github.solcott.activitytracker.composeApp.resources.gear_unretire
import io.github.solcott.activitytracker.composeApp.resources.gear_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.gear_weight
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.stringResource

/** The gear form: what the item is, who may see it, its photo, and for a saved item, retiring and deleting it. */
@CircuitInject(EditGearScreen::class, SessionScope::class)
@Composable
fun EditGearUi(state: EditGearState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = state.title.string(),
                onBack = { state.eventSink(EditGearEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadGear, { state.eventSink(EditGearEvent.Retry) }, contentPadding = padding) {
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, contentPadding = padding) {
                GearForm(state, Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp))
            }
        }
    }
    if (state.confirmingDelete) {
        AppAlertDialog(
            title = stringResource(Res.string.gear_delete_title),
            message = stringResource(Res.string.gear_delete_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.gear_retire)) { state.eventSink(EditGearEvent.Retire) },
                    AppDialogAction(stringResource(Res.string.gear_delete), destructive = true) {
                        state.eventSink(EditGearEvent.ConfirmDelete)
                    },
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(EditGearEvent.DismissDelete) },
        )
    }
}

@Composable
private fun GearForm(state: EditGearState, modifier: Modifier = Modifier) {
    val enabled = state.enabled
    val send = state.eventSink
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            state.name,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_name),
            enabled = enabled,
            isError = state.nameMissing,
            supportingText = if (state.nameMissing) stringResource(Res.string.gear_name_required) else null,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation.maxLength(EditGearLimits.name),
        )
        MenuField(
            shown = gearTypeLabel(state.type),
            label = stringResource(Res.string.gear_type),
            items =
                state.types
                    .map { type ->
                        AppMenuItem(gearTypeLabel(type), checked = type == state.type) { send(EditGearEvent.TypeChanged(type)) }
                    }
                    .toImmutableList(),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        if (state.showsKind) {
            MenuField(
                shown = state.kindLabel.string(),
                label = stringResource(Res.string.gear_kind),
                items =
                    state.kindOptions
                        .map { option ->
                            AppMenuItem(option.label.string(), checked = option.kind == state.kind) {
                                send(EditGearEvent.KindChanged(option.kind))
                            }
                        }
                        .toImmutableList(),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                state.brand,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_brand),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(EditGearLimits.brand),
            )
            AppTextField(
                state.model,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_model),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(EditGearLimits.brand),
            )
        }
        SportsField(state, enabled, onToggle = { send(EditGearEvent.ToggleSport(it)) })
        DefaultForField(state, enabled, onToggle = { send(EditGearEvent.ToggleDefault(it)) })
        GearCollectionsField(state.collections, enabled, onToggle = { send(EditGearEvent.ToggleCollection(it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                state.weight,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_weight),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = DigitsOnly.then(InputTransformation.maxLength(EditGearLimits.weightDigits)),
            )
            AcquiredField(state.acquiredOn, enabled, onChange = { send(EditGearEvent.AcquiredChanged(it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                state.startingDistance,
                Modifier.weight(1f),
                label = state.startingDistanceLabel.string(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(EditGearLimits.starting),
            )
            AppTextField(
                state.startingTime,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_starting_time),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(EditGearLimits.starting),
            )
        }
        Text(
            stringResource(Res.string.gear_starting_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhotoRow(state, enabled, send)
        AppTextField(
            state.notes,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_notes),
            enabled = enabled,
            inputTransformation = InputTransformation.maxLength(EditGearLimits.notes),
        )
        VisibilityPicker(state.visibility, { send(EditGearEvent.VisibilityChanged(it)) }, state.approvesFollowers, enabled = enabled)
        Text(
            stringResource(Res.string.gear_visibility_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let { ErrorText(it) }
        AppButton(onClick = { send(EditGearEvent.Save) }, Modifier.fillMaxWidth(), enabled = enabled) {
            Text(stringResource(Res.string.gear_save))
        }
        if (state.showsManagement) {
            HorizontalDivider()
            state.mountedOnLabel?.let { mounted ->
                AppTextButton(onClick = { send(EditGearEvent.OpenMountedOn) }) { Text(mounted.string()) }
            }
            state.retiredOnLabel?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.isRetired) {
                    AppSecondaryButton(onClick = { send(EditGearEvent.Retire) }, enabled = enabled) {
                        Text(stringResource(Res.string.gear_retire))
                    }
                } else {
                    AppSecondaryButton(onClick = { send(EditGearEvent.Unretire) }, enabled = enabled) {
                        Text(stringResource(Res.string.gear_unretire))
                    }
                }
                AppTextButton(onClick = { send(EditGearEvent.Delete) }, enabled = enabled) {
                    Text(stringResource(Res.string.gear_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** The sports the item is for, as a field that opens a sheet of every sport to tick. */
@Composable
private fun SportsField(state: EditGearState, enabled: Boolean, onToggle: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    TapField(
        state.sportsLabel.string(),
        stringResource(Res.string.gear_sports),
        onClick = { choosing = true },
        Modifier.fillMaxWidth(),
        enabled,
    )
    if (choosing) {
        val done = stringResource(Res.string.gear_sports_done)
        AppBottomSheet(onDismiss = { choosing = false }, sheetState = rememberAppSheetState()) {
            AppSheetHeader(
                title = stringResource(Res.string.gear_sports_choose),
                cancelLabel = stringResource(Res.string.action_cancel),
                onCancel = { choosing = false },
                confirmLabel = done,
                onConfirm = { choosing = false },
            )
            var expanded by rememberExpandedSports()
            LazyColumn(Modifier.fillMaxSize()) {
                sportTypeList(
                    SPORT_GROUPS,
                    expanded = expanded,
                    onToggleExpanded = { sport -> expanded = if (sport in expanded) expanded - sport else expanded + sport },
                    onClick = { onToggle(it.sport) },
                ) { type ->
                    AppCheckbox(state.isForSport(type.sport), onCheckedChange = null)
                }
            }
        }
    }
}

/**
 * The activity types new activities start with the item for ("Use by default for"), as a field that opens a sheet of every type, sub-sports
 * included, to tick.
 */
@Composable
private fun DefaultForField(state: EditGearState, enabled: Boolean, onToggle: (ActivityType) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TapField(
            state.defaultForLabel.string(),
            stringResource(Res.string.gear_default_for),
            onClick = { choosing = true },
            Modifier.fillMaxWidth(),
            enabled,
        )
        Text(
            stringResource(Res.string.gear_default_for_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (choosing) {
        AppBottomSheet(onDismiss = { choosing = false }, sheetState = rememberAppSheetState()) {
            AppSheetHeader(
                title = stringResource(Res.string.gear_default_for),
                cancelLabel = stringResource(Res.string.action_cancel),
                onCancel = { choosing = false },
                confirmLabel = stringResource(Res.string.gear_sports_done),
                onConfirm = { choosing = false },
            )
            var expanded by rememberExpandedSports()
            LazyColumn(Modifier.fillMaxSize()) {
                sportTypeList(
                    TYPE_GROUPS,
                    expanded = expanded,
                    onToggleExpanded = { sport -> expanded = if (sport in expanded) expanded - sport else expanded + sport },
                    onClick = onToggle,
                ) { type ->
                    AppCheckbox(state.isDefaultFor(type), onCheckedChange = null)
                }
            }
        }
    }
}

/** The groups the default-for sheet lists, chosen for both UIs (`SportTypeGroups.allTypes`). */
private val TYPE_GROUPS = SportTypeGroups.allTypes

/** The groups the sports sheet lists (`SportTypeGroups.gearSports`). */
private val SPORT_GROUPS = SportTypeGroups.gearSports

/** The date the athlete got the item, picked from a calendar, with a button to clear it. */
@Composable
private fun AcquiredField(date: LocalDate?, enabled: Boolean, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    PastDateField(
        shown = date?.let { EditGearTexts.date(it).string() }.orEmpty(),
        label = stringResource(Res.string.gear_acquired),
        chooseLabel = stringResource(Res.string.gear_acquired_choose),
        clearLabel = stringResource(Res.string.gear_acquired_clear),
        pickedMillis = date?.toPickerMillis(),
        onPick = { onChange(pickedDate(it)) },
        onClear = if (date != null) ({ onChange(null) }) else null,
        modifier = modifier,
        enabled = enabled,
    )
}

/** The item's photo, with buttons to pick one (compressed here, which drops its metadata) or remove it. */
@Composable
private fun PhotoRow(state: EditGearState, enabled: Boolean, send: (EditGearEvent) -> Unit) {
    val photo = state.photo
    val scope = rememberCoroutineScope()
    val pick =
        rememberFilePickerLauncher(type = FileKitType.Image) { file ->
            file ?: return@rememberFilePickerLauncher
            scope.launch {
                runSuspendCatching { compressPhoto(file.readBytes()) }
                    .onSuccess { send(EditGearEvent.PhotoPicked(PickedPhoto(ByteString(it.bytes), it.mimeType, it.width, it.height))) }
            }
        }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(PHOTO_SIZE).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            when (photo) {
                is GearPhoto.Saved ->
                    AsyncImage(photo.url, stringResource(Res.string.gear_photo), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                is GearPhoto.Picked -> {
                    val bytes = remember(photo) { photo.photo.bytes.toByteArray() }
                    AsyncImage(bytes, stringResource(Res.string.gear_photo), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                null -> Unit
            }
        }
        Column {
            AppTextButton(onClick = { pick.launch() }, enabled = enabled) { Text(state.photoAction.string()) }
            if (photo != null) {
                AppTextButton(onClick = { send(EditGearEvent.RemovePhoto) }, enabled = enabled) {
                    Text(stringResource(Res.string.gear_photo_remove))
                }
            }
        }
    }
}

/** Keeps only digits as the athlete types. */
private val DigitsOnly = InputTransformation { if (asCharSequence().any { !it.isDigit() }) revertAllChanges() }

private val PHOTO_SIZE = 72.dp
