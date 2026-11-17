package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Trail page (segments/path-page): what it says, and a selection shared with the map as browse's is. */
@OptIn(ExperimentalTestApi::class)
class PathUiTest {
    @Test
    fun theTrailSaysWhatItIsAndListsItsSegmentGroups() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.loaded) } }
        onNodeWithText("Trail · ", substring = true).assertExists()
        onNodeWithText("© OpenStreetMap contributors", substring = true).assertExists()
        row("Ann's").assertExists()
        row("Ann's: Lupine Hiking → Potato Hill").assertExists()
        onNodeWithText("Show all sports").assertDoesNotExist()
    }

    @Test
    fun onAPhoneATapSelectsAndTheSelectedRowOpens() = runComposeUiTest {
        val events = mutableListOf<PathEvent>()
        setContent { AppPreview(platform = UiPlatform.Android) { PathUi(PathSamples.selected.copy(eventSink = { events += it })) } }
        val stretch = PathSamples.selected.groups[1].shown.uri
        row("Ann's: Lupine Hiking → Potato Hill").assertIsSelected().assert(hasClickLabel("Open"))
        row("Ann's").assertIsNotSelected().assert(hasClickLabel("Show on map"))
        row("Ann's").performClick()
        assertEquals(PathEvent.TapRow(PathSamples.selected.groups[0].shown.uri), events.last())
        row("Ann's: Lupine Hiking → Potato Hill").performClick()
        assertEquals(PathEvent.TapRow(stretch), events.last())
    }

    @Test
    fun withAPointerAClickOpens() = runComposeUiTest {
        val events = mutableListOf<PathEvent>()
        setContent { AppPreview(platform = UiPlatform.Desktop) { PathUi(PathSamples.loaded.copy(eventSink = { events += it })) } }
        row("Ann's").performClick()
        assertEquals(PathEvent.Open(PathSamples.loaded.groups[0].shown.uri), events.last())
    }

    @Test
    fun narrowedToOneSportItOffersAll() = runComposeUiTest {
        val events = mutableListOf<PathEvent>()
        setContent { AppPreview { PathUi(PathSamples.ride.copy(eventSink = { events += it })) } }
        onNodeWithText("Show all sports").performClick()
        assertEquals(PathEvent.ShowAllSports, events.last())
    }

    @Test
    fun nothingToListSaysSo() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.empty) } }
        onNodeWithText("No segments here yet").assertExists()
    }

    /** The row named [name]: clickable, unlike the bar's title of the same name. */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.row(name: String) = onNode(hasText(name) and hasClickAction())

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
}
