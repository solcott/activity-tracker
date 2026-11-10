package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.search_clear
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.clearText
import org.jetbrains.compose.resources.stringResource

/**
 * A one-line search box over [query]: a search icon, a clear button while it holds text, and a Search key that hides the keyboard. With
 * [autoFocus], it opens ready to type, unless coming back to a search already made.
 */
@Composable
fun SearchField(query: TextFieldState, placeholder: String, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) SideEffect(Unit) { if (query.text.isEmpty()) focus.requestFocus() }
    AppTextField(
        query,
        modifier = modifier.focusRequester(focus),
        placeholder = placeholder,
        leadingIcon = { PlatformIcon(AppIcon.Search, null) },
        trailingIcon = {
            if (query.text.isNotEmpty()) {
                AppIconButton(
                    AppIcon.Clear,
                    stringResource(Res.string.search_clear),
                    onClick = { query.clearText() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboard?.hide() },
    )
}
