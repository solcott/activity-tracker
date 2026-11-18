package io.github.solcott.activitytracker.app.divesite

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.browse.MapBrowseEvent
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dive site browse (dive-sites/site-browse): the area's sites, the zoom limit, name results and their scope. */
@OptIn(ExperimentalTestApi::class)
class DiveSiteBrowseUiTest {
    @Test
    fun anAreaListsItsSitesNearestFirstAndOpensOne() = runComposeUiTest {
        val events = mutableListOf<DiveSiteBrowseEvent>()
        setContent {
            AppPreview(platform = UiPlatform.Desktop) { DiveSiteBrowseUi(DiveSiteBrowseSamples.loaded.copy(eventSink = { events += it })) }
        }
        onNodeWithText("SS Thistlegorm").assertExists()
        assertEquals(3, onAllNodesWithText("away", substring = true).fetchSemanticsNodes().size, "each site says how far away it is")
        onNodeWithText("This area").assertDoesNotExist()

        onNodeWithText("Shag Rock").performClick()
        assertEquals(DiveSiteBrowseEvent.Open(DiveSiteBrowseSamples.sites[1].site.uri), events.last(), "a click opens it")
    }

    @Test
    fun onAPhoneATapOnARowSelectsItAndTheSelectedRowOpens() = runComposeUiTest {
        val events = mutableListOf<DiveSiteBrowseEvent>()
        setContent {
            AppPreview(platform = UiPlatform.Android) {
                DiveSiteBrowseUi(DiveSiteBrowseSamples.selected.copy(eventSink = { events += it }))
            }
        }
        onNodeWithText("Shag Rock").assertIsSelected().assert(hasClickLabel("Open"))
        onNodeWithText("SS Thistlegorm").assertIsNotSelected().assert(hasClickLabel("Show on map"))

        onNodeWithText("SS Thistlegorm").performClick()
        assertEquals(DiveSiteBrowseEvent.TapRow(DiveSiteBrowseSamples.sites[0].site.uri), events.last())
        onNodeWithText("Shag Rock").performClick()
        assertEquals(DiveSiteBrowseEvent.TapRow(DiveSiteBrowseSamples.sites[1].site.uri), events.last(), "the presenter opens it")
    }

    @Test
    fun aMouseOverARowMarksItWithoutSelectingIt() = runComposeUiTest {
        val events = mutableListOf<DiveSiteBrowseEvent>()
        var marked = Color.Unspecified
        setContent {
            AppPreview(platform = UiPlatform.Desktop) {
                marked = MaterialTheme.colorScheme.secondaryContainer
                DiveSiteBrowseUi(DiveSiteBrowseSamples.loaded.copy(eventSink = { events += it }))
            }
        }
        val row = onNodeWithText("Shag Rock")
        val plain = row.cornerColor()
        assertTrue(plain.distanceTo(marked) > MARK_TOLERANCE, "not marked before the pointer comes")

        row.performMouseInput { enter(center) }
        val hovering = row.cornerColor()
        assertTrue(hovering.distanceTo(marked) < hovering.distanceTo(plain), "marked while the pointer is over it, was $hovering")
        row.assertIsNotSelected()
        assertTrue(events.isEmpty(), "hovering sends nothing")

        row.performMouseInput { exit(Offset(-10f, -10f)) }
        assertEquals(plain, row.cornerColor(), "unmarked once it leaves")
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
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.tooWide) } }
        onNodeWithText("Zoom in to see dive sites").assertExists()
    }

    @Test
    fun nameResultsOfferTheScope() = runComposeUiTest {
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.nameResults) } }
        onNodeWithText("SS Thistlegorm").assertExists()
        onNodeWithText("This area").assertExists()
        onNodeWithText("Everywhere").assertExists()
        onNodeWithText("away", substring = true).assertDoesNotExist()
    }

    @Test
    fun nothingInThisAreaOffersEverywhere() = runComposeUiTest {
        val events = mutableListOf<MapBrowseEvent>()
        val state = DiveSiteBrowseSamples.nothingHere
        setContent { AppPreview { DiveSiteBrowseUi(state.copy(browse = state.browse.copy(eventSink = { events += it }))) } }
        onNodeWithText("No dive sites here match “thistle”.").assertExists()
        onNodeWithText("Search everywhere").performClick()
        assertEquals(MapBrowseEvent.SearchEverywhere, events.last())
    }

    @Test
    fun withNowhereToOpenItAsksTheAthlete() = runComposeUiTest {
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.nowhere) } }
        onNodeWithText("Move the map to a place, or search dive sites by name.").assertExists()
    }
}
