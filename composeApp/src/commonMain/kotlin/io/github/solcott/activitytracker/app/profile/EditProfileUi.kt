package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.AvatarFrame
import io.github.solcott.activitytracker.app.ui.Countries
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.countryName
import io.github.solcott.activitytracker.app.ui.detailGroupLabel
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.genderLabel
import io.github.solcott.activitytracker.app.ui.rememberReadOnlyTextFieldState
import io.github.solcott.activitytracker.app.ui.stripDiacritics
import io.github.solcott.activitytracker.app.ui.visibilityScopeLabel
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_birth_date
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_birth_date_choose
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_clear
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_country
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_description
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_details_heading
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_details_help
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_display_name
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_gender
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_gender_describe
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_height_cm
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_height_ft
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_height_in
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_length
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_locality
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_not_set
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_now_hidden
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_photo_change
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_photo_remove
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_region
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_title
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_weight_kg
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_weight_lb
import io.github.solcott.activitytracker.composeApp.resources.gender_self_describe
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.stringResource

@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfileUi(state: EditProfileState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.edit_profile_title),
                onBack = { state.eventSink(EditProfileEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, contentPadding = padding) {
            Column(
                Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarRow(state)
                LimitedTextField(
                    state.displayName,
                    label = stringResource(Res.string.edit_profile_display_name),
                    maxLength = EditProfileState.DISPLAY_NAME_MAX_LENGTH,
                    enabled = state.loaded,
                    singleLine = true,
                )
                LimitedTextField(
                    state.description,
                    label = stringResource(Res.string.edit_profile_description),
                    maxLength = EditProfileState.DESCRIPTION_MAX_LENGTH,
                    enabled = state.loaded,
                    singleLine = false,
                )
                HorizontalDivider()
                Text(stringResource(Res.string.edit_profile_details_heading), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.edit_profile_details_help), style = MaterialTheme.typography.bodySmall)
                DetailSection(DetailGroup.Location, state) {
                    LimitedTextField(
                        state.locality,
                        label = stringResource(Res.string.edit_profile_locality),
                        maxLength = EditProfileState.DETAIL_MAX_LENGTH,
                        enabled = state.loaded,
                        singleLine = true,
                    )
                    LimitedTextField(
                        state.region,
                        label = stringResource(Res.string.edit_profile_region),
                        maxLength = EditProfileState.DETAIL_MAX_LENGTH,
                        enabled = state.loaded,
                        singleLine = true,
                    )
                    CountryField(state.country, { state.eventSink(EditProfileEvent.CountryChanged(it)) }, enabled = state.loaded)
                }
                DetailSection(DetailGroup.BirthDate, state) {
                    BirthDateField(state.birthDate, { state.eventSink(EditProfileEvent.BirthDateChanged(it)) }, enabled = state.loaded)
                }
                DetailSection(DetailGroup.Gender, state) {
                    GenderField(state.gender, { state.eventSink(EditProfileEvent.GenderChanged(it)) }, enabled = state.loaded)
                }
                DetailSection(DetailGroup.Body, state) {
                    HeightFields(state)
                    AppTextField(
                        state.weight,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.loaded,
                        label =
                            stringResource(
                                when (state.units) {
                                    UnitSystem.Metric -> Res.string.edit_profile_weight_kg
                                    UnitSystem.Imperial -> Res.string.edit_profile_weight_lb
                                }
                            ),
                        isError = !WeightConversions.isValidInputText(state.weight.text.toString(), state.units),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                state.nowHidden.forEach { group ->
                    Text(
                        stringResource(Res.string.edit_profile_now_hidden, detailGroupLabel(group)),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                if (state.error?.kind == UiError.Kind.SignInAgain) {
                    AppSecondaryButton(onClick = { state.eventSink(EditProfileEvent.SignInAgain) }) {
                        Text(stringResource(Res.string.action_sign_in_again))
                    }
                }
                AppButton(
                    onClick = { state.eventSink(EditProfileEvent.Save) },
                    enabled = state.loaded && state.valid && !state.saving,
                ) {
                    Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
                }
            }
        }
    }
}

/** The profile picture, with buttons to pick a new one (downscaled and compressed here) or remove it. */
@Composable
private fun AvatarRow(state: EditProfileState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val pick =
        rememberFilePickerLauncher(type = FileKitType.Image) { file ->
            file ?: return@rememberFilePickerLauncher
            scope.launch {
                runSuspendCatching { compressAvatar(file.readBytes(), AVATAR_MAX_PIXELS, AVATAR_JPEG_QUALITY) }
                    .onSuccess { state.eventSink(EditProfileEvent.AvatarPicked(ByteString(it))) }
            }
        }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val name = state.displayName.text.toString()
        when (val avatar = state.avatar) {
            is AvatarImage.Saved -> Avatar(avatar.url, name, AVATAR_SIZE)
            is AvatarImage.Picked ->
                AvatarFrame(name, AVATAR_SIZE) {
                    val bytes = remember(avatar) { avatar.jpeg.toByteArray() }
                    AsyncImage(bytes, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            null -> Avatar(null, name, AVATAR_SIZE)
        }
        Column {
            AppTextButton(onClick = { pick.launch() }, enabled = state.loaded) {
                Text(stringResource(Res.string.edit_profile_photo_change))
            }
            if (state.avatar != null) {
                AppTextButton(onClick = { state.eventSink(EditProfileEvent.RemoveAvatar) }, enabled = state.loaded) {
                    Text(stringResource(Res.string.edit_profile_photo_remove))
                }
            }
        }
    }
}

/** Avatars are shown small, so 512 px JPEGs stay well under the lexicon's 1 MB limit. */
private const val AVATAR_MAX_PIXELS = 512
private const val AVATAR_JPEG_QUALITY = 85
private val AVATAR_SIZE = 72.dp

/** One detail group's fields, headed by its name and followed by who may see all of them. */
@Composable
private fun DetailSection(
    group: DetailGroup,
    state: EditProfileState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(detailGroupLabel(group), style = MaterialTheme.typography.titleSmall)
        content()
        // Named for every field it covers, so it doesn't read as belonging to the last field above it.
        Text(visibilityScopeLabel(group), style = MaterialTheme.typography.labelMedium)
        VisibilityPicker(
            group.visibility(state.visibility),
            { state.eventSink(EditProfileEvent.VisibilityChanged(group, it)) },
            Modifier.fillMaxWidth(),
            enabled = state.loaded,
        )
    }
}

/** Height in cm, or in feet and inches side by side for imperial. */
@Composable
private fun HeightFields(state: EditProfileState, modifier: Modifier = Modifier) {
    val isError = !state.heightValid
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    when (state.units) {
        UnitSystem.Metric ->
            AppTextField(
                state.height,
                modifier = modifier.fillMaxWidth(),
                enabled = state.loaded,
                label = stringResource(Res.string.edit_profile_height_cm),
                isError = isError,
                keyboardOptions = keyboard,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        UnitSystem.Imperial ->
            Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    state.height,
                    modifier = Modifier.weight(1f),
                    enabled = state.loaded,
                    label = stringResource(Res.string.edit_profile_height_ft),
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                AppTextField(
                    state.heightInches,
                    modifier = Modifier.weight(1f),
                    enabled = state.loaded,
                    label = stringResource(Res.string.edit_profile_height_in),
                    isError = isError,
                    keyboardOptions = keyboard,
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
    }
}

/** A country chosen by typing part of its localized name. [code] is ISO 3166-1 alpha-2, or empty. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryField(
    code: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val countries = remember { Countries.codes.map { CountryOption(it, countryName(it)) }.sortedBy { it.name } }
    val selectedName = if (code.isEmpty()) "" else countryName(code)
    val query = rememberTextFieldState(selectedName)
    // The name shows once the profile has loaded, and again after a pick.
    SideEffect(selectedName) { query.setTextAndPlaceCursorAtEnd(selectedName) }
    var expanded by remember { mutableStateOf(false) }
    val typed = query.text.toString()
    val matches =
        if (typed == selectedName) {
            countries
        } else {
            val key = searchKey(typed.trim())
            // A typed code ("us") puts its country above the names that merely contain it (Australia, Belarus…).
            val (byCode, byName) = countries.partition { it.code.equals(key, ignoreCase = true) }
            byCode + byName.filter { it.key.contains(key) }
        }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }, modifier = modifier) {
        AppTextField(
            query,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled).fillMaxWidth(),
            enabled = enabled,
            label = stringResource(Res.string.edit_profile_country),
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
            // Typing opens the menu; setting the text from code doesn't.
            inputTransformation = InputTransformation { expanded = true },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query.setTextAndPlaceCursorAtEnd(selectedName)
            },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.edit_profile_not_set)) },
                onClick = {
                    onSelect("")
                    expanded = false
                },
            )
            matches.forEach { (countryCode, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(countryCode)
                        query.setTextAndPlaceCursorAtEnd(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A country in [CountryField]'s menu. [key] is [name] as the search matches it, folded once rather than per keystroke. */
private data class CountryOption(val code: String, val name: String, val key: String = searchKey(name))

/** Folds case and accents, so "cote" finds "Côte d'Ivoire". */
private fun searchKey(text: String): String = stripDiacritics(text).lowercase()

/** A past date from Material's date picker, shown as `YYYY-MM-DD`. [date] is empty when unset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(
    date: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    AppTextField(
        rememberReadOnlyTextFieldState(date),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = true,
        label = stringResource(Res.string.edit_profile_birth_date),
        trailingIcon = {
            Row {
                if (date.isNotEmpty()) {
                    AppIconButton(
                        AppIcon.Clear,
                        stringResource(Res.string.edit_profile_clear),
                        { onChange("") },
                        enabled = enabled,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIconButton(
                    AppIcon.Calendar,
                    stringResource(Res.string.edit_profile_birth_date_choose),
                    { picking = true },
                    enabled = enabled,
                )
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
    if (picking) {
        val now = remember { Clock.System.now().toEpochMilliseconds() }
        val pastOnly = remember(now) { DateBounds(maxDateMillis = now) }
        AppDatePickerDialog(
            initialSelectedDateMillis = CalendarDate.toPickerMillis(date),
            selectableDates = pastOnly,
            onConfirm = { millis ->
                millis?.let { onChange(CalendarDate.fromPickerMillis(it)) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** One of the known genders, or the athlete's own words. [gender] is empty when unset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderField(
    state: TextFieldState,
    onPick: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Kept until a known value is chosen, so clearing the description doesn't hide its field.
    var choseSelfDescribe by remember { mutableStateOf(false) }
    val gender = state.text.toString()
    val selfDescribe = choseSelfDescribe || (gender.isNotEmpty() && gender !in Gender.all)
    val shown =
        when {
            selfDescribe -> stringResource(Res.string.gender_self_describe)
            gender.isEmpty() -> stringResource(Res.string.edit_profile_not_set)
            else -> genderLabel(gender)
        }
    fun choose(value: String, describe: Boolean) {
        choseSelfDescribe = describe
        onPick(value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val notSet = stringResource(Res.string.edit_profile_not_set)
        val describeLabel = stringResource(Res.string.gender_self_describe)
        val knownLabels = Gender.all.associateWith { genderLabel(it) }
        val items = buildList {
            add(AppMenuItem(notSet, checked = gender.isEmpty() && !selfDescribe) { choose("", describe = false) })
            knownLabels.forEach { (known, label) ->
                add(AppMenuItem(label, checked = gender == known && !selfDescribe) { choose(known, describe = false) })
            }
            add(AppMenuItem(describeLabel, checked = selfDescribe) { choose(if (gender in Gender.all) "" else gender, describe = true) })
        }
            .toImmutableList()
        MenuField(shown, stringResource(Res.string.edit_profile_gender), items, Modifier.fillMaxWidth(), enabled = enabled)
        if (selfDescribe) {
            LimitedTextField(
                state,
                label = stringResource(Res.string.edit_profile_gender_describe),
                maxLength = EditProfileState.DETAIL_MAX_LENGTH,
                enabled = enabled,
                singleLine = true,
            )
        }
    }
}

/** A text field with a length counter that turns to an error past [maxLength]. */
@Composable
private fun LimitedTextField(
    state: TextFieldState,
    label: String,
    maxLength: Int,
    enabled: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val length = state.text.length
    AppTextField(
        state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label,
        supportingText = stringResource(Res.string.edit_profile_length, length, maxLength),
        isError = length > maxLength,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3),
    )
}
