package io.github.solcott.activitytracker.textfield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

/**
 * A text field's text as snapshot state, behind members Swift export can carry. Only native fields edit it: SwiftUI's field owns the cursor
 * and the selection, so this holds the text alone.
 */
@Stable
actual class TextFieldState internal constructor(initialText: String) {
    private var current by mutableStateOf(initialText)

    actual val text: CharSequence
        get() = current

    /** The field's text, for Swift to read and set. Setting the text it already holds changes nothing. */
    var value: String
        get() = current
        set(value) {
            if (current != value) current = value
        }

    /**
     * [value] now and each time it changes, whoever changed it: a presenter that seeds, clears or restores the field, or Swift itself. A
     * Swift field shows what it emits (dev/ios-swift-interop). Setting [value] to the text it holds emits nothing, so text a Swift field
     * wrote doesn't come back to it as a change.
     */
    val values: Flow<String>
        get() = snapshotFlow { current }
}

/** Saves a field's text, which is all it holds, and restores a field holding it. */
internal val TextFieldStateSaver: Saver<TextFieldState, String> = Saver(save = { it.value }, restore = { TextFieldState(it) })

actual fun textFieldStateOf(initialText: String): TextFieldState = TextFieldState(initialText)

@Composable
actual fun rememberTextFieldState(initialText: String): TextFieldState =
    rememberSaveable(saver = TextFieldStateSaver) { TextFieldState(initialText) }

actual fun TextFieldState.setTextAndPlaceCursorAtEnd(text: String) {
    value = text
}

actual fun TextFieldState.clearText() {
    value = ""
}
