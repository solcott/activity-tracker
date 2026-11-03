package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.segments.GroupChoice
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings' Segments & groups page: segment matching (segments/segment-track) and grouping (feed/group-activities). */
@OptIn(ExperimentalTestApi::class)
class SegmentsAndGroupsUiTest {
    @Test
    fun theLeaderboardChoiceShowsUnderMatchingWhileItsOn() = runComposeUiTest {
        val events = mutableListOf<SegmentsAndGroupsEvent>()
        setContent { AppPreview { SegmentsAndGroupsUi(SegmentsAndGroupsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Show me on segment leaderboards").performClick()
        assertEquals(SegmentsAndGroupsEvent.LeaderboardsChanged(false), events.last())
    }

    @Test
    fun noLeaderboardChoiceWhileMatchingIsOff() = runComposeUiTest {
        setContent { AppPreview { SegmentsAndGroupsUi(SegmentsAndGroupsSamples.default.copy(segmentMatching = false)) } }
        onNodeWithText("Show me on segment leaderboards").assertDoesNotExist()
    }

    @Test
    fun pickingAGroupChoiceSendsIt() = runComposeUiTest {
        val events = mutableListOf<SegmentsAndGroupsEvent>()
        setContent { AppPreview { SegmentsAndGroupsUi(SegmentsAndGroupsSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Group activities").performScrollTo().assertExists()
        onNodeWithText("Links your activities with others'", substring = true).assertExists()
        onNodeWithText("Anyone").performScrollTo().performClick()
        assertEquals(SegmentsAndGroupsEvent.GroupActivitiesChanged(GroupChoice.ANYONE), events.last())
    }

    @Test
    fun anOldSignInIsToldToSignInAgain() = runComposeUiTest {
        setContent { AppPreview { SegmentsAndGroupsUi(SegmentsAndGroupsSamples.default.copy(canUseGroups = false)) } }
        onNodeWithText("Sign in again to use group activities.").performScrollTo().assertExists()
        onNodeWithText("Anyone").assertIsNotEnabled()
    }
}
