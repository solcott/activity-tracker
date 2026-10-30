package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        TextField(
            state = state,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = lineLimits,
            inputTransformation = inputTransformation,
            shape = RoundedCornerShape(10.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHighest,
                    unfocusedContainerColor = colors.surfaceContainerHighest,
                    disabledContainerColor = colors.surfaceContainerHighest,
                    errorContainerColor = colors.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = colors.error,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
