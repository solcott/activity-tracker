package io.github.solcott.activitytracker.app.divesite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dive site browse (dive-sites/site-browse): the area's sites, the zoom limit, name results and their scope. */
@OptIn(ExperimentalTestApi::class)
class DiveSiteBrowseUiTest {
    @Test
    fun anAreaListsItsSitesNearestFirstAndOpensOne() = runComposeUiTest {
        val events = mutableListOf<DiveSiteBrowseEvent>()
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("SS Thistlegorm").assertExists()
        assertEquals(3, onAllNodesWithText("away", substring = true).fetchSemanticsNodes().size, "each site says how far away it is")
        onNodeWithText("This area").assertDoesNotExist()

        onNodeWithText("Shag Rock").performClick()
        assertEquals(DiveSiteBrowseEvent.Open(DiveSiteBrowseSamples.sites[1].site.uri), events.last())
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
        val events = mutableListOf<DiveSiteBrowseEvent>()
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.nothingHere.copy(eventSink = { events += it })) } }
        onNodeWithText("No dive sites here match “thistle”.").assertExists()
        onNodeWithText("Search everywhere").performClick()
        assertEquals(DiveSiteBrowseEvent.SearchEverywhere, events.last())
    }

    @Test
    fun withNowhereToOpenItAsksTheAthlete() = runComposeUiTest {
        setContent { AppPreview { DiveSiteBrowseUi(DiveSiteBrowseSamples.nowhere) } }
        onNodeWithText("Move the map to a place, or search dive sites by name.").assertExists()
    }
}
