package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** After leaving on another device, with work still on this one (auth/leaving). */
@OptIn(ExperimentalTestApi::class)
class LeftElsewhereUiTest {
    @Test
    fun saysWhatsPendingAndOffersBothChoices() = runComposeUiTest {
        val events = mutableListOf<LeftElsewhereEvent>()
        setContent { AppPreview { LeftElsewhereUi(LeftElsewhereSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("1 change is pending.").assertExists()
        onNodeWithText("Discard and sign out").performClick()
        assertEquals(LeftElsewhereEvent.Discard, events.last())
        onNodeWithText("Rejoin").performClick()
        assertEquals(LeftElsewhereEvent.Rejoin, events.last())
    }

    @Test
    fun listsEveryKindOfWork() = runComposeUiTest {
        setContent { AppPreview { LeftElsewhereUi(LeftElsewhereSamples.all.getValue("everything")) } }
        onNodeWithText("12 changes are pending.").assertExists()
        onNodeWithText("An import is still saving.").assertExists()
        onNodeWithText("A data export hasn't been saved.").assertExists()
        onNodeWithText("A recording is in progress.").assertExists()
    }
}
