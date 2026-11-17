package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.location.SegmentHeading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The segment page's choice of its segment group's sport groups and directions (segments/segment-groups). */
@OptIn(ExperimentalTestApi::class)
class SegmentGroupUiTest {
    @Test
    fun choosingASportSwitchesToItsSegment() = runComposeUiTest {
        val events = mutableListOf<SegmentEvent>()
        val state = SegmentSamples.group
        setContent { AppPreview { SegmentUi(state.copy(eventSink = { events += it })) } }
        onNodeWithText("Hike").performClick()
        assertEquals(state.group?.sportEvent("hike"), events.last())
        val other = state.group!!.directions.first { !state.group!!.isCurrent(it) }
        onNodeWithText(GroupSwitcherTexts.direction(other).let { (it as AppText.Named).name }.let(::englishOf)).performClick()
        assertEquals(state.group!!.directionEvent(other), events.last())
    }

    @Test
    fun aSegmentAloneOffersNoChoice() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.loaded) } }
        onNodeWithText("Hike").assertDoesNotExist()
    }

    @Test
    fun everyDirectionHasItsString() {
        val names = SegmentHeading.entries.map { "segment_heading_${it.word}" }
        assertTrue(names.all { it in Res.allStringResources }, "missing ${names.filterNot { it in Res.allStringResources }}")
    }

    private fun englishOf(name: String): String = name.removePrefix("segment_heading_").replaceFirstChar { it.uppercase() }

    @Test
    fun aSwitchLoadsWithoutTheRefreshRow() {
        val state = SegmentSamples.group
        val segment = state.segment!!
        assertTrue(state.copy(shown = segment.uri).showsRefreshProgress, "a refresh of the shown segment")
        val other = state.group!!.options.first { it.uri != segment.uri }.uri
        assertFalse(state.copy(shown = other).showsRefreshProgress, "another of the group loading over it")
    }
}
