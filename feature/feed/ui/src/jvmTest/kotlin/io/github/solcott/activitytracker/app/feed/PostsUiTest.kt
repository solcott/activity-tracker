package io.github.solcott.activitytracker.app.feed

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Posts in the timeline (feed/posts): the shared proposal as it stands, voting from the card, and opening its segment. */
@OptIn(ExperimentalTestApi::class)
class PostsUiTest {
    private fun postEvents(events: List<FeedEvent>) = events.filterIsInstance<FeedEvent.Post>().map { it.event }

    @Test
    fun theCardShowsTheProposalAndVotes() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.posts.copy(eventSink = { events += it })) } }
        onNodeWithText("Everyone at the trailhead already calls it this.").assertExists()
        onNodeWithText("Proposes “Wiggly Woods”").assertExists()
        onNodeWithText("Open for votes", substring = true).assertExists()
        onNodeWithText("Adopted", substring = true).assertExists()
        onNodeWithTag(TestTags.POST_VOTE_UP, useUnmergedTree = true).performClick()
        val vote = assertIs<PostEvent.Vote>(postEvents(events).last())
        assertEquals(SegmentNameVoteDirection.UP, vote.direction)
    }

    @Test
    fun tappingTheCardOpensItsSegment() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.posts.copy(eventSink = { events += it })) } }
        onNodeWithText("Proposes “Wiggly Woods”").performClick()
        assertIs<PostEvent.Open>(postEvents(events).last())
    }
}
