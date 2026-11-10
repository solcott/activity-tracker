package io.github.solcott.activitytracker.textfield

import androidx.compose.foundation.text.input.TextFieldState as ComposeTextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class TextFieldStateIosTest {
    @Test
    fun `setting value changes the text`() {
        val state = textFieldStateOf("a")

        state.value = "alice.test"

        assertEquals("alice.test", state.text.toString())
        assertEquals("alice.test", state.value)
    }

    @Test
    fun `clearing empties value`() {
        val state = textFieldStateOf("a comment")

        state.clearText()

        assertEquals("", state.value)
    }

    @Test
    fun `text the presenter sets is the value Swift reads`() {
        val state = textFieldStateOf()

        state.setTextAndPlaceCursorAtEnd("Morning ride")

        assertEquals("Morning ride", state.value)
    }

    @Test
    fun `setting the value it already holds leaves the selection alone`() {
        val state = textFieldStateOf("hello")
        val compose = state.composeState as ComposeTextFieldState
        compose.edit { selection = TextRange(2) }

        state.value = "hello"

        assertEquals(TextRange(2), compose.selection)
    }

    @Test
    fun `the Compose field and Swift edit one text`() {
        val state = textFieldStateOf("al")

        (state.composeState as ComposeTextFieldState).edit { append("ice") }

        assertEquals("alice", state.value)
    }
}
