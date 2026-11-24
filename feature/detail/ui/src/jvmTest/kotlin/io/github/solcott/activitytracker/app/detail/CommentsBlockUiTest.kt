package io.github.solcott.activitytracker.app.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewEvent
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The activity page's comments block (feed/comments): the count and first comment near the top, each opening the Comments screen. */
@OptIn(ExperimentalTestApi::class)
class CommentsBlockUiTest {
    @Test
    fun theBlockShowsNearTheTopAndOpensTheCommentsScreen() = runComposeUiTest {
        val events = mutableListOf<CommentsPreviewEvent>()
        val page = ActivityDetailSamples.page.let { it.copy(comments = it.comments.copy(eventSink = { e -> events += e })) }
        setContent { AppPreview { ActivityDetailUi(ActivityDetailState(ContentStates.loaded(page), eventSink = {})) } }
        // On screen before any card is scrolled to.
        onNodeWithContentDescription("1 comment").assertExists().performClick()
        assertEquals(CommentsPreviewEvent.OpenAll, events.last())
        onNodeWithText("Great ride!").assertExists().performClick()
        assertEquals(CommentsPreviewEvent.OpenAll, events.last())
        onNodeWithText("Add a comment").assertExists().performClick()
        assertEquals(CommentsPreviewEvent.Compose, events.last())
    }
}
