package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.textfield.TextFieldState

/**
 * A text field over a [TextFieldState]. It is an outlined field with a floating [label]. A [placeholder] always shows in an empty field,
 * with the [label] floating above it.
 */
@Composable
fun AppTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    inputTransformation: InputTransformation? = null,
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
