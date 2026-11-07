package io.github.solcott.activitytracker.app.post

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** A post's quotes (feed/quote-posts): the cards, the empty list, and a post the viewer may no longer see. */
@OptIn(ExperimentalTestApi::class)
class PostQuotesUiTest {
    @Test
    fun theQuotesAreListedAsCards() = runComposeUiTest {
        setContent { AppPreview { PostQuotesUi(PostQuotesSamples.loaded) } }
        onNodeWithText("Quotes").assertExists()
        onAllNodesWithText("Yes, and the lower half should be its own segment.").assertCountEquals(1)
        onNodeWithText("Post unavailable").assertExists()
    }

    @Test
    fun noQuotesSaysSo() = runComposeUiTest {
        setContent { AppPreview { PostQuotesUi(PostQuotesSamples.all.getValue("empty")) } }
        onNodeWithText("No quotes of this post yet.").assertExists()
    }

    @Test
    fun aPostTheViewerMayNoLongerSeeSaysSo() = runComposeUiTest {
        setContent { AppPreview { PostQuotesUi(PostQuotesSamples.notFound) } }
        onNodeWithText("This post isn't available", substring = true).assertExists()
    }
}
