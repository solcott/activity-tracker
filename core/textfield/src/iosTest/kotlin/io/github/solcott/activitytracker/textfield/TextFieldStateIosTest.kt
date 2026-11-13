package io.github.solcott.activitytracker.textfield

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `a saved field comes back with its text`() {
        val state = textFieldStateOf("Morning ride")

        val saved = with(TextFieldStateSaver) { SaverScope { true }.save(state) }
        val restored = TextFieldStateSaver.restore(checkNotNull(saved))

        assertEquals("Morning ride", restored?.value)
    }

    @Test
    fun `values follows every change and says nothing when the text is set to itself`() = runTest {
        val state = textFieldStateOf("a")
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.values.collect { seen += it } }

        // Each write is applied as the app's running composition applies it.
        state.value = "b"
        Snapshot.sendApplyNotifications()
        state.clearText()
        Snapshot.sendApplyNotifications()
        state.setTextAndPlaceCursorAtEnd("c")
        Snapshot.sendApplyNotifications()
        state.value = "c"
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("a", "b", "", "c"), seen)
    }
}
