package io.github.solcott.activitytracker.app.feed

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The timeline's import card and the batch screen it opens (feed/import-cards). */
@OptIn(ExperimentalTestApi::class)
class ImportCardsUiTest {
    @Test
    fun theCardSaysWhoImportedWhatAndOpensTheBatch() = runComposeUiTest {
        var opened = 0
        setContent { AppPreview { ImportCard(FeedSamples.importCard, onClick = { opened++ }) } }
        onNodeWithText("Imported 312 activities").assertExists()
        onNodeWithText("Ride 201", substring = true).assertExists()
        onNodeWithText("Run 98", substring = true).assertExists()
        onNodeWithText("Imported 312 activities").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theBatchListsItsActivitiesUnderTheirMonths() = runComposeUiTest {
        val events = mutableListOf<ImportBatchEvent>()
        setContent { AppPreview { ImportBatchUi(ImportBatchSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("Imported by Bob").assertExists()
        onNodeWithText("Evening Ride", substring = true).performClick()
        val open = assertIs<ImportBatchEvent.Activity>(events.last { it is ImportBatchEvent.Activity })
        assertIs<ActivityListEvent.Open>(open.event)
    }

    @Test
    fun anEmptyBatchSaysSo() = runComposeUiTest {
        setContent { AppPreview { ImportBatchUi(ImportBatchSamples.all.getValue("empty")) } }
        onNodeWithText("None of this import's activities are shown to you.").assertExists()
    }
}
