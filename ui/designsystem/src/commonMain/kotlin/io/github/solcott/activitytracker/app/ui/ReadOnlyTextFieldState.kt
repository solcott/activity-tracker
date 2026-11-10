package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf

/**
 * The state of a `readOnly` text field that shows [text], like a picker's chosen value. It isn't saved: it's derived from [text], so it's
 * rebuilt whenever that changes. A field the athlete types in uses `rememberTextFieldState` instead (in the presenter, if it owns the
 * value).
 */
@Composable fun rememberReadOnlyTextFieldState(text: String): TextFieldState = remember(text) { textFieldStateOf(text) }
