package io.github.solcott.activitytracker.app.record

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

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
        setContent {
            AppPreview {
                ImportActivityUi(
                    state.copy(batch = state.batch?.copy(exports = persistentListOf(BatchExport(ExportKind.Strava, privacy = false))))
                )
            }
        }
        onNodeWithText("doesn't say who could see each activity", substring = true).assertExists()
    }

    @Test
    fun twoExportsPickedTogetherEachGetANote() = runComposeUiTest {
        val state = ImportActivitySamples.all.getValue("batchExport")
        val exports = persistentListOf(BatchExport(ExportKind.Strava, privacy = true), BatchExport(ExportKind.Garmin, privacy = true))
        setContent { AppPreview { ImportActivityUi(state.copy(batch = state.batch?.copy(exports = exports))) } }
        onNodeWithText("From a Strava export", substring = true).assertExists()
        onNodeWithText("From a Garmin Connect export", substring = true).assertExists()
    }

    @Test
    fun exactCopiesAreLabelledAndCounted() = runComposeUiTest {
        // Filtered to duplicates, so the copy's row is near the top of the lazy list.
        setContent { AppPreview { ImportActivityUi(ImportActivitySamples.all.getValue("batchDuplicates")) } }
        onNodeWithText("1 is an exact copy of what it repeats.", substring = true).assertExists()
        onNodeWithText("Exact copy of #2 in this import").assertExists()
    }

    @Test
    fun theDuplicatesFilterSetsEveryShownDuplicateAtOnce() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("batchDuplicates").copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("Set every duplicate shown to:").assertExists()
        // The bulk buttons come before the rows' own choices.
        onAllNodesWithText("Ignore").onFirst().performClick()
        assertEquals(
            listOf(ImportActivityEvent.ShownDuplicatesAction(DuplicateAction.Ignore)),
            events.filterIsInstance<ImportActivityEvent.ShownDuplicatesAction>(),
        )
    }

    @Test
    fun anOpenedDuplicateIsComparedWithWhatItRepeats() = runComposeUiTest {
        setContent { AppPreview { ImportActivityUi(ImportActivitySamples.batchPreview) } }
        onNodeWithText("This activity").assertExists()
        onNodeWithText("Earlier in this import").assertExists()
        onNodeWithText("Exact copy").assertExists()
        onAllNodesWithText("Garmin Forerunner 965").onFirst().assertExists()
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

    @Test
    fun aSaveThatStoppedShortSaysWhatWasSavedBySave() = runComposeUiTest {
        setContent { AppPreview { ImportActivityUi(ImportActivitySamples.all.getValue("batchSaveFailed")) } }
        // Save is the batch list's last item.
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Saved 3 of 5. The rest weren't saved:", substring = true))
        onNodeWithText("Saved 3 of 5. The rest weren't saved:", substring = true).assertExists()
    }

    @Test
    fun importOpenedDuringASaveShowsItAndOpensTheQueue() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("savingImport").copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("412 of 1,362 saved", substring = true).assertExists()
        onNodeWithText("View uploads").performClick()
        assertEquals(listOf<ImportActivityEvent>(ImportActivityEvent.Saved.ViewUploads), events)
    }

    @Test
    fun aStoppedImportSaysToPickTheFilesAgainAndCanBeDismissed() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("stoppedImport").copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("stopped after saving 120 of 400", substring = true).assertExists()
        onNodeWithText("Dismiss").performClick()
        assertEquals(listOf<ImportActivityEvent>(ImportActivityEvent.StoppedImportDismissed("3kbatch")), events)
    }

    @Test
    fun aSingleFileSaysOnlyThisDevicesActivitiesWereCheckedAndRetries() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("duplicateCheckFailed").copy(eventSink = { events += it })
        setContent { AppPreview { ImportActivityUi(state) } }
        onNodeWithText("only activities on this device were checked", substring = true).performScrollTo().assertExists()
        onNodeWithText("Retry").performScrollTo().performClick()
        assertEquals(listOf<ImportActivityEvent>(ImportActivityEvent.RetryDuplicateCheck), events)
    }
}
