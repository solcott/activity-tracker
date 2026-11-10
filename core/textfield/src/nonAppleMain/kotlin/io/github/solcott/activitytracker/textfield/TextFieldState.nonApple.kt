// This file is where the app's TextFieldState meets Compose's.
@file:Suppress("UseAppTextFieldState")

package io.github.solcott.activitytracker.textfield

import androidx.compose.foundation.text.input.clearText as composeClearText
import androidx.compose.foundation.text.input.rememberTextFieldState as composeRememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd as composeSetTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable

actual typealias TextFieldState = androidx.compose.foundation.text.input.TextFieldState

actual fun textFieldStateOf(initialText: String): TextFieldState = TextFieldState(initialText)

@Composable actual fun rememberTextFieldState(initialText: String): TextFieldState = composeRememberTextFieldState(initialText)

actual fun TextFieldState.setTextAndPlaceCursorAtEnd(text: String) = composeSetTextAndPlaceCursorAtEnd(text)

actual fun TextFieldState.clearText() = composeClearText()
