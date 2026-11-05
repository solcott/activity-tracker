package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** Segment browse (segments/segment-browse): the area's runs, the zoom limit, name results and their scope. */
@OptIn(ExperimentalTestApi::class)
class SegmentBrowseUiTest {
    @Test
    fun anAreaListsItsRunsNearestFirstAndOpensOne() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("Ridge Trail").assertExists()
        onNodeWithText("Hogback Climb").assertExists()
        assertEquals(3, onAllNodesWithText("away", substring = true).fetchSemanticsNodes().size, "each run says how far away it is")
        onNodeWithText("This area").assertDoesNotExist()

        onNodeWithText("Hogback Climb").performClick()
        assertEquals(SegmentBrowseEvent.Open(SegmentBrowseSamples.runs[1].segment.uri), events.last())
    }

    @Test
    fun zoomedOutItAsksToZoomIn() = runComposeUiTest {
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.tooWide) } }
        onNodeWithText("Zoom in to see segments").assertExists()
    }

    @Test
    fun nameResultsOfferTheScope() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.nameResults.copy(eventSink = { events += it })) } }
        onNodeWithText("Hogback Climb").assertExists()
        onNodeWithText("This area").assertExists()
        onNodeWithText("Everywhere").assertExists()
        onNodeWithText("away", substring = true).assertDoesNotExist()
    }

    @Test
    fun nothingInThisAreaOffersEverywhere() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.nothingHere.copy(eventSink = { events += it })) } }
        onNodeWithText("No segments here match “hog”.").assertExists()
        onNodeWithText("Search everywhere").performClick()
        assertEquals(SegmentBrowseEvent.SearchEverywhere, events.last())
    }

    @Test
    fun withNowhereToOpenItAsksTheAthlete() = runComposeUiTest {
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.nowhere) } }
        onNodeWithText("Move the map to a place, or search segments by name.").assertExists()
    }

    @Test
    fun aNewSearchOpensAtItsBestMatch() = runComposeUiTest {
        val old = (1..30).map { fakeSegment("3kold$it", record = fakeSegmentRecord("Hog Canyon $it")) }
        val new = (1..30).map { fakeSegment("3knew$it", record = fakeSegmentRecord(if (it == 1) "Hogback Climb" else "Hogback $it")) }
        var results by mutableStateOf(PagedStates.loaded(old))
        setContent { AppPreview { SegmentBrowseUi(SegmentBrowseSamples.nameResults.copy(results = results)) } }
        onNodeWithText("Hog Canyon 1").assertIsDisplayed()

        // The old results stay under a refresh row while the new search loads, then the new ones replace them.
        results = PagedStates.refreshing(old, Unit)
        waitForIdle()
        results = PagedStates.end(new, Unit)
        onNodeWithText("Hogback Climb").assertIsDisplayed()
    }
}
