package io.github.solcott.activitytracker.textfield

import androidx.compose.foundation.text.input.TextFieldState as ComposeTextFieldState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextFieldStateJvmTest {
    @Test
    fun `off iOS the state is Compose's own`() {
        val state: Any = textFieldStateOf("a")

        assertIs<ComposeTextFieldState>(state)
        assertEquals("a", state.text.toString())
    }

    @Test
    fun `the app's functions and Compose's edit act on one text`() {
        val state = textFieldStateOf("a")

        state.setTextAndPlaceCursorAtEnd("alice")
        assertEquals("alice", state.text.toString())
        assertEquals(5, state.selection.start)

        state.edit { append(".test") }
        assertEquals("alice.test", state.text.toString())

        state.clearText()
        assertEquals("", state.text.toString())
    }

    @Test
    fun `a state made with no text is empty`() {
        assertEquals("", textFieldStateOf().text.toString())
    }
}
