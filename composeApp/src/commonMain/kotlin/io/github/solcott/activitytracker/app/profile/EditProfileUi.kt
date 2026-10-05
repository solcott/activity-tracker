package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.Countries
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.countryName
import io.github.solcott.activitytracker.app.ui.detailGroupLabel
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.genderLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_ok
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
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
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_length
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_locality
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_not_set
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_now_hidden
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
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfileUi(state: EditProfileState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(EditProfileEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LimitedTextField(
                value = state.displayName,
                onValueChange = { state.eventSink(EditProfileEvent.DisplayNameChanged(it)) },
                label = stringResource(Res.string.edit_profile_display_name),
                maxLength = EditProfileState.DISPLAY_NAME_MAX_LENGTH,
                enabled = state.loaded,
                singleLine = true,
            )
            LimitedTextField(
                value = state.description,
                onValueChange = { state.eventSink(EditProfileEvent.DescriptionChanged(it)) },
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
                    value = state.locality,
                    onValueChange = { state.eventSink(EditProfileEvent.LocalityChanged(it)) },
                    label = stringResource(Res.string.edit_profile_locality),
                    maxLength = EditProfileState.DETAIL_MAX_LENGTH,
                    enabled = state.loaded,
                    singleLine = true,
                )
                LimitedTextField(
                    value = state.region,
                    onValueChange = { state.eventSink(EditProfileEvent.RegionChanged(it)) },
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
            DetailSection(DetailGroup.Weight, state) {
                OutlinedTextField(
                    state.weight,
                    { state.eventSink(EditProfileEvent.WeightChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.loaded,
                    label = {
                        Text(
                            stringResource(
                                when (state.units) {
                                    UnitSystem.Metric -> Res.string.edit_profile_weight_kg
                                    UnitSystem.Imperial -> Res.string.edit_profile_weight_lb
                                }
                            )
                        )
                    },
                    isError = !WeightConversions.isValidInputText(state.weight, state.units),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
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
            Button(
                onClick = { state.eventSink(EditProfileEvent.Save) },
                enabled = state.loaded && state.valid && !state.saving,
            ) {
                Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
            }
        }
    }
}

/** One detail's fields, headed by its name and followed by who may see it. */
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
        VisibilityPicker(
            group.visibility(state.visibility),
            { state.eventSink(EditProfileEvent.VisibilityChanged(group, it)) },
            Modifier.fillMaxWidth(),
            enabled = state.loaded,
        )
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
    val countries = remember { Countries.codes.map { it to countryName(it) }.sortedBy { it.second } }
    val selectedName = if (code.isEmpty()) "" else countryName(code)
    var query by remember(code) { mutableStateOf(selectedName) }
    var expanded by remember { mutableStateOf(false) }
    val matches = if (query == selectedName) countries else countries.filter { it.second.contains(query.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }, modifier = modifier) {
        OutlinedTextField(
            query,
            {
                query = it
                expanded = true
            },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled).fillMaxWidth(),
            enabled = enabled,
            label = { Text(stringResource(Res.string.edit_profile_country)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = selectedName
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
                        query = name
                        expanded = false
                    },
                )
            }
        }
    }
}

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
    OutlinedTextField(
        date,
        {},
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = true,
        label = { Text(stringResource(Res.string.edit_profile_birth_date)) },
        trailingIcon = {
            Row {
                if (date.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }, enabled = enabled) {
                        Icon(Icons.Filled.Clear, stringResource(Res.string.edit_profile_clear))
                    }
                }
                IconButton(onClick = { picking = true }, enabled = enabled) {
                    Icon(Icons.Filled.DateRange, stringResource(Res.string.edit_profile_birth_date_choose))
                }
            }
        },
        singleLine = true,
    )
    if (picking) {
        val now = remember { Clock.System.now().toEpochMilliseconds() }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = CalendarDate.toPickerMillis(date),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now
                    },
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onChange(CalendarDate.fromPickerMillis(it)) }
                        picking = false
                    }
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) {
            DatePicker(pickerState)
        }
    }
}

/** One of the known genders, or the athlete's own words. [gender] is empty when unset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderField(
    gender: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Kept until a known value is chosen, so clearing the description doesn't hide its field.
    var choseSelfDescribe by remember { mutableStateOf(false) }
    val selfDescribe = choseSelfDescribe || (gender.isNotEmpty() && gender !in Gender.all)
    val shown =
        when {
            selfDescribe -> stringResource(Res.string.gender_self_describe)
            gender.isEmpty() -> stringResource(Res.string.edit_profile_not_set)
            else -> genderLabel(gender)
        }
    fun choose(value: String, describe: Boolean) {
        choseSelfDescribe = describe
        onChange(value)
        expanded = false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }) {
            OutlinedTextField(
                shown,
                {},
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
                enabled = enabled,
                readOnly = true,
                label = { Text(stringResource(Res.string.edit_profile_gender)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.edit_profile_not_set)) },
                    onClick = { choose("", describe = false) },
                )
                Gender.all.forEach { known ->
                    DropdownMenuItem(text = { Text(genderLabel(known)) }, onClick = { choose(known, describe = false) })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gender_self_describe)) },
                    onClick = { choose(if (gender in Gender.all) "" else gender, describe = true) },
                )
            }
        }
        if (selfDescribe) {
            LimitedTextField(
                value = gender,
                onValueChange = onChange,
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
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    enabled: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        supportingText = { Text(stringResource(Res.string.edit_profile_length, value.length, maxLength)) },
        isError = value.length > maxLength,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
    )
}
