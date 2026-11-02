package io.github.solcott.activitytracker.app.feed

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.segments.GroupChoice
import kotlin.test.Test
import kotlin.test.assertEquals

/** The feed's one-time question about group activities (feed/group-activities). */
@OptIn(ExperimentalTestApi::class)
class GroupsPromptUiTest {
    @Test
    fun eachAnswerIsSent() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.askingGroups.copy(eventSink = { events += it })) } }
        onNodeWithText("Link activities you do together?").assertExists()
        onNodeWithText("People I follow").performClick()
        onNodeWithText("Anyone").performClick()
        onNodeWithText("Not now").performClick()
        assertEquals(
            listOf(GroupChoice.CONNECTED, GroupChoice.ANYONE, GroupChoice.OFF),
            events.filterIsInstance<FeedEvent.AnswerGroups>().map { it.choice },
        )
    }

    @Test
    fun itIsntShownUnlessAsked() = runComposeUiTest {
        setContent { AppPreview { FeedUi(FeedSamples.loaded) } }
        onNodeWithText("Link activities you do together?").assertDoesNotExist()
    }
}
