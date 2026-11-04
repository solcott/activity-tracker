package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** A lift's screen (segments/segment-lifts): the viewer's rides, and no leaderboard, Mayor or names. */
@OptIn(ExperimentalTestApi::class)
class SegmentLiftUiTest {
    @Test
    fun aLiftHasNoLeaderboardOrNames() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.lift) } }
        onNodeWithText("Your rides").assertExists()
        onNodeWithText("Leaderboard").assertDoesNotExist()
        onNodeWithText("Your efforts").assertDoesNotExist()
    }

    @Test
    fun anActiveRunStillOffersItsLeaderboard() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.loaded) } }
        onNodeWithText("Your efforts").assertExists()
    }
}
