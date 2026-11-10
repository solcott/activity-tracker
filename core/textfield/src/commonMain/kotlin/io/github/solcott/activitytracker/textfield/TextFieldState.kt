package io.github.solcott.activitytracker.textfield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * The state of one text field, which a screen's `State` holds and its presenter owns.
 *
 * On Android, desktop and web this is Compose's `TextFieldState` itself, so the Compose text field API (`edit { }`, the Material fields)
 * works on it unchanged. On iOS it is a class Swift export can carry, from which Swift reads and sets the text. Common code makes one with
 * [textFieldStateOf] or [rememberTextFieldState]: the class declares no constructor because Compose's takes a Compose type.
 */
@Stable
expect class TextFieldState {
    val text: CharSequence
}

/** A text field state holding [initialText], for a `State`'s default and for sample states. A presenter uses [rememberTextFieldState]. */
expect fun textFieldStateOf(initialText: String = ""): TextFieldState

/** Remembers a text field state that survives what Compose's own `rememberTextFieldState` survives. */
@Composable expect fun rememberTextFieldState(initialText: String = ""): TextFieldState

/** Replaces the text with [text] and puts the cursor after it. */
expect fun TextFieldState.setTextAndPlaceCursorAtEnd(text: String)

/** Empties the field. */
expect fun TextFieldState.clearText()
