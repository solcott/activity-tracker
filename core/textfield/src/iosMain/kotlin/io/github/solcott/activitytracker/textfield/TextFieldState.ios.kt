// This file is where the app's TextFieldState meets Compose's.
@file:Suppress("UseAppTextFieldState")

package io.github.solcott.activitytracker.textfield

import androidx.compose.foundation.text.input.TextFieldState as ComposeTextFieldState
import androidx.compose.foundation.text.input.clearText as composeClearText
import androidx.compose.foundation.text.input.rememberTextFieldState as composeRememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd as composeSetTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

/**
 * Wraps a Compose `TextFieldState`, which the Compose field on iOS still edits directly, behind members Swift export can carry. When
 * Compose no longer renders on iOS the inside becomes plain snapshot state and [composeState] goes; no contract changes.
 */
@Stable
actual class TextFieldState internal constructor(internal val compose: ComposeTextFieldState) {
    actual val text: CharSequence
        get() = compose.text

    /** The field's text, for Swift to read and set. Setting the text it already holds changes nothing, the cursor included. */
    var value: String
        get() = compose.text.toString()
        set(value) {
            if (!compose.text.contentEquals(value)) compose.composeSetTextAndPlaceCursorAtEnd(value)
        }

    /**
     * [value] now and each time it changes, whoever changed it: a presenter that seeds, clears or restores the field, the Compose field on
     * iOS, or Swift itself. A Swift field shows what it emits (dev/ios-swift-interop). Setting [value] to the text it holds emits nothing,
     * so text a Swift field wrote doesn't come back to it as a change.
     */
    val values: Flow<String>
        get() = snapshotFlow { compose.text.toString() }

    /**
     * The Compose `TextFieldState` inside, for the design system's `AppTextField` alone, which casts it back. Typed `Any` because a public
     * member typed with a class Swift export hides is generated as a stub that stops the app.
     */
    val composeState: Any
        get() = compose
}

actual fun textFieldStateOf(initialText: String): TextFieldState = TextFieldState(ComposeTextFieldState(initialText))

@Composable
actual fun rememberTextFieldState(initialText: String): TextFieldState {
    // Compose's own remember, so the text is saved and restored exactly as before; the wrapper is remade around a restored state.
    val compose = composeRememberTextFieldState(initialText)
    return remember(compose) { TextFieldState(compose) }
}

actual fun TextFieldState.setTextAndPlaceCursorAtEnd(text: String) = compose.composeSetTextAndPlaceCursorAtEnd(text)

actual fun TextFieldState.clearText() = compose.composeClearText()
