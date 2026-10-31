package io.github.solcott.activitytracker.app.record

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Several activities to import at once (feed/batch-import). */
@OptIn(ExperimentalTestApi::class)
class ImportBatchUiTest {
    @Test
    fun aBatchListsItsRowsAndSendsToggleFilterAndOpen() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("batch").copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("5 activities to import").assertExists()
        // A zone-less file's notice says which file it's about.
        onNodeWithText("dives.uddf logs local time", substring = true).assertExists()
        onNodeWithText("Easy run").assertExists()
        onNodeWithText("Duplicates").performClick()
        onNodeWithText("Easy run").performClick()
        onNodeWithText("Deselect all").assertExists()
        assertEquals(
            listOf(ImportActivityEvent.FilterChanged(BatchFilter.Duplicates), ImportActivityEvent.ItemOpened(0)),
            events.filter { it is ImportActivityEvent.FilterChanged || it is ImportActivityEvent.ItemOpened },
        )
    }

    @Test
    fun anExportSaysWhereItsTitlesComeFromAndWhatWasntRead() = runComposeUiTest {
        setContent { AppPreview { ImportActivityUi(ImportActivitySamples.all.getValue("batchExport")) } }
        onNodeWithText("From a Strava export", substring = true).assertExists()
        onNodeWithText("4410.tcx.gz (damaged)", substring = true).assertExists()
        onNodeWithText("Private in the export, so saved as Only me").assertExists()
    }

    @Test
    fun anExportWithoutPrivacySaysEveryActivityTakesTheChosenVisibility() = runComposeUiTest {
        val state = ImportActivitySamples.all.getValue("batchExport")
        setContent { AppPreview { ImportActivityUi(state.copy(batch = state.batch?.copy(exportPrivacy = false))) } }
        onNodeWithText("doesn't say who could see each activity", substring = true).assertExists()
    }

    @Test
    fun anOpenedActivityOffersOnlyItsTitleAndType() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.batchPreview.copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("Title").assertExists()
        onNodeWithText("Description").assertDoesNotExist()
        onNodeWithText("Done").performClick()
        assertTrue(ImportActivityEvent.ItemClosed in events)
    }

    @Test
    fun aSavedBatchSaysHowManyWereSavedAndSkipped() = runComposeUiTest {
        setContent { AppPreview { ImportActivityUi(ImportActivitySamples.all.getValue("batchSaved")) } }
        onNodeWithText("1,986 activities saved.", substring = true).assertExists()
        onNodeWithText("3 couldn't be read again", substring = true).assertExists()
    }
}
