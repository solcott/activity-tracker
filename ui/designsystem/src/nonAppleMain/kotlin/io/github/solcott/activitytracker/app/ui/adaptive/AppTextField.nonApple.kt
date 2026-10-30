package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AppTextField(
    state: TextFieldState,
    modifier: Modifier,
    label: String?,
    placeholder: String?,
    enabled: Boolean,
    readOnly: Boolean,
    isError: Boolean,
    supportingText: String?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    lineLimits: TextFieldLineLimits,
    inputTransformation: InputTransformation?,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        label = label?.let { { Text(it) } },
        labelPosition = TextFieldLabelPosition.Cutout(isAlwaysMinimized = placeholder != null),
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        inputTransformation = inputTransformation,
    )
}
