package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.compressPhoto
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.rememberExpandedSports
import io.github.solcott.activitytracker.app.ui.rememberReadOnlyTextFieldState
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.app.ui.sportTypeList
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired_choose
import io.github.solcott.activitytracker.composeApp.resources.gear_acquired_clear
import io.github.solcott.activitytracker.composeApp.resources.gear_brand
import io.github.solcott.activitytracker.composeApp.resources.gear_default_for
import io.github.solcott.activitytracker.composeApp.resources.gear_default_for_hint
import io.github.solcott.activitytracker.composeApp.resources.gear_default_for_none
import io.github.solcott.activitytracker.composeApp.resources.gear_delete
import io.github.solcott.activitytracker.composeApp.resources.gear_delete_message
import io.github.solcott.activitytracker.composeApp.resources.gear_delete_title
import io.github.solcott.activitytracker.composeApp.resources.gear_edit_title
import io.github.solcott.activitytracker.composeApp.resources.gear_kind
import io.github.solcott.activitytracker.composeApp.resources.gear_kind_none
import io.github.solcott.activitytracker.composeApp.resources.gear_model
import io.github.solcott.activitytracker.composeApp.resources.gear_name
import io.github.solcott.activitytracker.composeApp.resources.gear_name_required
import io.github.solcott.activitytracker.composeApp.resources.gear_new_title
import io.github.solcott.activitytracker.composeApp.resources.gear_notes
import io.github.solcott.activitytracker.composeApp.resources.gear_photo
import io.github.solcott.activitytracker.composeApp.resources.gear_photo_add
import io.github.solcott.activitytracker.composeApp.resources.gear_photo_change
import io.github.solcott.activitytracker.composeApp.resources.gear_photo_remove
import io.github.solcott.activitytracker.composeApp.resources.gear_retire
import io.github.solcott.activitytracker.composeApp.resources.gear_retired_on
import io.github.solcott.activitytracker.composeApp.resources.gear_save
import io.github.solcott.activitytracker.composeApp.resources.gear_sports
import io.github.solcott.activitytracker.composeApp.resources.gear_sports_choose
import io.github.solcott.activitytracker.composeApp.resources.gear_sports_done
import io.github.solcott.activitytracker.composeApp.resources.gear_sports_none
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_distance_km
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_distance_mi
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_help
import io.github.solcott.activitytracker.composeApp.resources.gear_starting_time
import io.github.solcott.activitytracker.composeApp.resources.gear_type
import io.github.solcott.activitytracker.composeApp.resources.gear_unretire
import io.github.solcott.activitytracker.composeApp.resources.gear_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.gear_weight
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableList
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
                title = stringResource(if (state.isNew) Res.string.gear_new_title else Res.string.gear_edit_title),
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
    val enabled = !state.saving
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
            inputTransformation = InputTransformation.maxLength(GearItemRecord.MAX_NAME),
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
        if (state.kinds.isNotEmpty()) {
            val none = stringResource(Res.string.gear_kind_none)
            MenuField(
                shown = state.kind?.let { gearKindLabel(state.type, it) } ?: none,
                label = stringResource(Res.string.gear_kind),
                items =
                    (listOf(AppMenuItem(none, checked = state.kind == null) { send(EditGearEvent.KindChanged(null)) }) +
                            state.kinds.map { kind ->
                                AppMenuItem(gearKindLabel(state.type, kind), checked = kind == state.kind) {
                                    send(EditGearEvent.KindChanged(kind))
                                }
                            })
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
                inputTransformation = InputTransformation.maxLength(GearItemRecord.MAX_BRAND),
            )
            AppTextField(
                state.model,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_model),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(GearItemRecord.MAX_BRAND),
            )
        }
        SportsField(state.sports, enabled, onToggle = { send(EditGearEvent.ToggleSport(it)) })
        DefaultForField(state.defaultFor, enabled, onToggle = { send(EditGearEvent.ToggleDefault(it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                state.weight,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_weight),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = DigitsOnly.then(InputTransformation.maxLength(MAX_WEIGHT_DIGITS)),
            )
            AcquiredField(state.acquiredOn, enabled, onChange = { send(EditGearEvent.AcquiredChanged(it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                state.startingDistance,
                Modifier.weight(1f),
                label =
                    stringResource(
                        when (state.units) {
                            UnitSystem.Metric -> Res.string.gear_starting_distance_km
                            UnitSystem.Imperial -> Res.string.gear_starting_distance_mi
                        }
                    ),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(MAX_STARTING_CHARS),
            )
            AppTextField(
                state.startingTime,
                Modifier.weight(1f),
                label = stringResource(Res.string.gear_starting_time),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(MAX_STARTING_CHARS),
            )
        }
        Text(
            stringResource(Res.string.gear_starting_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PhotoRow(state.photo, enabled, send)
        AppTextField(
            state.notes,
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_notes),
            enabled = enabled,
            inputTransformation = InputTransformation.maxLength(GearItemRecord.MAX_NOTES),
        )
        VisibilityPicker(state.visibility, { send(EditGearEvent.VisibilityChanged(it)) }, state.approvesFollowers, enabled = enabled)
        Text(
            stringResource(Res.string.gear_visibility_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        AppButton(onClick = { send(EditGearEvent.Save) }, Modifier.fillMaxWidth(), enabled = enabled) {
            Text(stringResource(Res.string.gear_save))
        }
        if (!state.isNew) {
            HorizontalDivider()
            state.retiredOn?.let {
                Text(stringResource(Res.string.gear_retired_on, it.toString()), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.retiredOn == null) {
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
private fun SportsField(sports: ImmutableList<String>, enabled: Boolean, onToggle: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val shown = if (sports.isEmpty()) stringResource(Res.string.gear_sports_none) else sports.map { sportLabel(it) }.joinToString(", ")
    Box(Modifier.fillMaxWidth()) {
        AppTextField(
            rememberReadOnlyTextFieldState(shown),
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gear_sports),
            enabled = enabled,
            readOnly = true,
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        // A read-only field doesn't report clicks, so a transparent layer on top opens the sheet.
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.Button) { choosing = true })
    }
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
                    AppCheckbox(type.sport in sports, onCheckedChange = null)
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
private fun DefaultForField(types: ImmutableList<ActivityType>, enabled: Boolean, onToggle: (ActivityType) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val shown =
        if (types.isEmpty()) stringResource(Res.string.gear_default_for_none) else types.map { activityTypeLabel(it) }.joinToString(", ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth()) {
            AppTextField(
                rememberReadOnlyTextFieldState(shown),
                Modifier.fillMaxWidth(),
                label = stringResource(Res.string.gear_default_for),
                enabled = enabled,
                readOnly = true,
                trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            // A read-only field doesn't report clicks, so a transparent layer on top opens the sheet.
            Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.Button) { choosing = true })
        }
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
                    AppCheckbox(type in types, onCheckedChange = null)
                }
            }
        }
    }
}

/** Every activity type by category, sub-sports included: a default can be for a sport or for one of its sub-sports. */
private val TYPE_GROUPS =
    SportCatalog.categories.map { group -> group.category to group.types.distinct().toImmutableList<ActivityType>() }.toImmutableList()

/** Each category's bare sports: gear is for a sport, not a sub-sport. */
private val SPORT_GROUPS =
    SportCatalog.categories
        .map { group -> group.category to group.types.filter { it.subSport == null }.distinct().toImmutableList<ActivityType>() }
        .filter { it.second.isNotEmpty() }
        .toImmutableList()

/** The date the athlete got the item, picked from a calendar, with a button to clear it. */
@Composable
private fun AcquiredField(date: LocalDate?, enabled: Boolean, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    AppTextField(
        rememberReadOnlyTextFieldState(date?.toString().orEmpty()),
        modifier,
        label = stringResource(Res.string.gear_acquired),
        enabled = enabled,
        readOnly = true,
        trailingIcon = {
            Row {
                if (date != null) {
                    AppIconButton(
                        AppIcon.Clear,
                        stringResource(Res.string.gear_acquired_clear),
                        { onChange(null) },
                        enabled = enabled,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIconButton(AppIcon.Calendar, stringResource(Res.string.gear_acquired_choose), { picking = true }, enabled = enabled)
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
    if (picking) {
        val clock = LocalClock.current
        val now = remember { clock.now().toEpochMilliseconds() }
        val pastOnly = remember(now) { DateBounds(maxDateMillis = now) }
        AppDatePickerDialog(
            initialSelectedDateMillis = date?.let { it.toEpochDays() * MILLIS_PER_DAY },
            selectableDates = pastOnly,
            onConfirm = { millis ->
                millis?.let { onChange(LocalDate.fromEpochDays(it.floorDiv(MILLIS_PER_DAY).toInt())) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The item's photo, with buttons to pick one (compressed here, which drops its metadata) or remove it. */
@Composable
private fun PhotoRow(photo: GearPhoto?, enabled: Boolean, send: (EditGearEvent) -> Unit) {
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
            AppTextButton(onClick = { pick.launch() }, enabled = enabled) {
                Text(stringResource(if (photo == null) Res.string.gear_photo_add else Res.string.gear_photo_change))
            }
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

private const val MAX_WEIGHT_DIGITS = 7

/** Room for a used bike's 100,000 km, or a helmet's thousands of hours, to a decimal. */
private const val MAX_STARTING_CHARS = 9
private const val MILLIS_PER_DAY = 86_400_000L
private val PHOTO_SIZE = 72.dp
