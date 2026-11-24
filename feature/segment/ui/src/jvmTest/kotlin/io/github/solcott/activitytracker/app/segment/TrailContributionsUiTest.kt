package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.app.TrailDetail as Detail
import kotlin.test.Test
import kotlin.test.assertEquals

/** Riders keeping a segment's trail details true on its screen (segments/trail-detail-suggestions). */
@OptIn(ExperimentalTestApi::class)
class TrailContributionsUiTest {
    private val events = mutableListOf<TrailEvent>()

    private fun show(state: SegmentState, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview { SegmentUi(state.copy(trail = state.trail.copy(eventSink = { events += it }))) } }
        checks()
    }

    @Test
    fun rowsShowTheirSourceAndOpenTheirSheet() =
        show(SegmentSamples.trailCommunity) {
            onNodeWithText("Riders confirmed these in Nov 2026").performScrollTo().assertExists()
            onNodeWithText("Community · 12 riders").performScrollTo().assertExists()
            onNodeWithText("Riders report this may have changed").performScrollTo().assertExists()
            onNodeWithText("No dogs").performScrollTo().performClick()
            assertEquals(TrailEvent.Open(Detail.DOG), events.last())
        }

    @Test
    fun theSheetConfirmsAndVotes() =
        show(SegmentSamples.trailSheet) {
            onNodeWithText("Still right").performClick()
            onNodeWithText("Agree").performScrollTo().performClick()
            onNodeWithText("Suggest a different value").performScrollTo().performClick()
            val maya = SegmentSamples.trailSheet.segment!!.trailSuggestions.single().uri.value
            assertEquals(
                listOf(TrailEvent.Confirm(still = true), TrailEvent.Vote(maya, up = true), TrailEvent.Suggest(Detail.LIT)),
                events,
            )
        }

    @Test
    fun thePickerMarksTheCurrentValueAndSends() =
        show(SegmentSamples.trailSuggest) {
            onNodeWithText("Current").assertExists()
            onNodeWithText("Road width").performScrollTo().performClick()
            onNodeWithText("Suggest").performClick()
            assertEquals(listOf(TrailEvent.Pick(2), TrailEvent.Send), events)
        }

    @Test
    fun theQuickCheckAnswersEachDetail() =
        show(SegmentSamples.trailQuickCheck) {
            onNodeWithText("You rode this recently").performScrollTo().assertExists()
            onNodeWithContentDescription("Dogs allowed: it's changed", substring = true).assertDoesNotExist()
            onNodeWithText("All still right").performScrollTo().performClick()
            assertEquals(TrailEvent.AllStillRight, events.last())
        }

    @Test
    fun aRegularIsInvitedToDescribeAnEmptySegment() =
        show(SegmentSamples.trailInvite) {
            onNodeWithText("Nobody has described this segment yet.").performScrollTo().assertExists()
            onNodeWithText("Surface").performScrollTo().performClick()
            assertEquals(TrailEvent.Suggest(Detail.SURFACE), events.last())
        }
}
