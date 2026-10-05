package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_description
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_display_name
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_length
import io.github.solcott.activitytracker.composeApp.resources.edit_profile_title
import io.github.solcott.activitytracker.data.di.SessionScope
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
            Modifier.padding(padding).padding(16.dp),
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
