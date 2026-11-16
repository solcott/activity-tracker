package io.github.solcott.activitytracker.app.segment

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Segment browse (segments/segment-browse): the area's runs, the zoom limit, name results and their scope. */
@OptIn(ExperimentalTestApi::class)
class SegmentBrowseUiTest {
    @Test
    fun anAreaListsItsRunsNearestFirstAndOpensOne() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        setContent {
            AppPreview(platform = UiPlatform.Desktop) { SegmentBrowseUi(SegmentBrowseSamples.loaded.copy(eventSink = { events += it })) }
        }
        onNodeWithText("Ridge Trail").assertExists()
        onNodeWithText("Hogback Climb").assertExists()
        assertEquals(3, onAllNodesWithText("away", substring = true).fetchSemanticsNodes().size, "each run says how far away it is")
        onNodeWithText("This area").assertDoesNotExist()

        onNodeWithText("Hogback Climb").performClick()
        assertEquals(SegmentBrowseEvent.Open(SegmentBrowseSamples.runs[1].segment.uri), events.last(), "a click opens it")
    }

    @Test
    fun onAPhoneATapOnARowSelectsItAndTheSelectedRowOpens() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        setContent {
            AppPreview(platform = UiPlatform.Android) { SegmentBrowseUi(SegmentBrowseSamples.selected.copy(eventSink = { events += it })) }
        }
        val hogback = SegmentBrowseSamples.runs[1].segment
        onNodeWithText("Hogback Climb").assertIsSelected().assert(hasClickLabel("Open"))
        onNodeWithText("Ridge Trail").assertIsNotSelected().assert(hasClickLabel("Show on map"))

        onNodeWithText("Ridge Trail").performClick()
        assertEquals(SegmentBrowseEvent.TapRow(SegmentBrowseSamples.runs[0].segment.uri), events.last())
        onNodeWithText("Hogback Climb").performClick()
        assertEquals(SegmentBrowseEvent.TapRow(hogback.uri), events.last(), "the presenter opens the selected row")
    }

    @Test
    fun aMouseOverARowMarksItWithoutSelectingIt() = runComposeUiTest {
        val events = mutableListOf<SegmentBrowseEvent>()
        var marked = Color.Unspecified
        setContent {
            AppPreview(platform = UiPlatform.Desktop) {
                marked = MaterialTheme.colorScheme.secondaryContainer
                SegmentBrowseUi(SegmentBrowseSamples.loaded.copy(eventSink = { events += it }))
            }
        }
        val ridge = onNodeWithText("Ridge Trail")
        val plain = ridge.cornerColor()
        assertTrue(plain.distanceTo(marked) > MARK_TOLERANCE, "not marked before the pointer comes")

        // Material's own hover layer tints whatever the row draws, so the marked background shows a shade darker.
        ridge.performMouseInput { enter(center) }
        val hovering = ridge.cornerColor()
        assertTrue(hovering.distanceTo(marked) < hovering.distanceTo(plain), "marked while the pointer is over it, was $hovering")
        ridge.assertIsNotSelected()
        assertTrue(events.isEmpty(), "hovering sends nothing")

        ridge.performMouseInput { exit(Offset(-10f, -10f)) }
        assertEquals(plain, ridge.cornerColor(), "unmarked once it leaves")
    }

    /** The colour just inside a row's top-left corner, where only its background is drawn. */
    private fun SemanticsNodeInteraction.cornerColor(): Color = captureToImage().toPixelMap()[2, 2]

    private fun Color.distanceTo(other: Color): Float = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private companion object {
        /** How far apart, summed over red, green and blue, two backgrounds must be to tell them apart. */
        const val MARK_TOLERANCE = 0.05f
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
