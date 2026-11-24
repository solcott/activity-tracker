package io.github.solcott.activitytracker.app.comments

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Comments screen (feed/comments): the threads, the field at the foot, and the focus it takes. */
@OptIn(ExperimentalTestApi::class)
class CommentsUiTest {
    @Test
    fun showsTheThreadsTheCountAndTheField() = runComposeUiTest {
        setContent { AppPreview { CommentsUi(CommentsSamples.all.getValue("loaded")) } }
        onNodeWithText("Comments").assertExists() // The bar's title; the list says only the count.
        onNodeWithText("Ridge loop").assertExists()
        onNodeWithText("3 comments").assertExists()
        onNodeWithText("Legs were cooked by the top.").assertExists()
        onNodeWithText("Add a comment").assertExists()
    }

    @Test
    fun theFieldTakesFocusWhenOpenedToComment() = runComposeUiTest {
        setContent { AppPreview { CommentsUi(CommentsSamples.all.getValue("loaded").copy(focus = true)) } }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun replySendsItsEvent() = runComposeUiTest {
        val events = mutableListOf<CommentEvent>()
        val state = CommentsSamples.all.getValue("loaded")
        val page = state.page!!
        val sink = state.copy(content = state.content.copy(data = page.copy(comments = page.comments.copy(eventSink = { events += it }))))
        setContent { AppPreview { CommentsUi(sink) } }
        onAllNodesWithText("Reply")[0].performClick()
        assertEquals(CommentEvent.ReplyTo(page.comments.paging.items.first().uri), events.single())
    }
}
