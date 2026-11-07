package io.github.solcott.activitytracker.app.feed

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeRepostedPost
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Posts in the timeline (feed/posts): the shared proposal as it stands, voting from the card, and opening the post or its segment. */
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
    fun tappingTheCardOpensThePost() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.posts.copy(eventSink = { events += it })) } }
        onNodeWithText("Proposes “Wiggly Woods”").performClick()
        assertIs<PostEvent.OpenPost>(postEvents(events).last())
    }

    @Test
    fun tappingTheSegmentLineOpensTheSegment() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.posts.copy(eventSink = { events += it })) } }
        onAllNodesWithText("Name for segment", substring = true).onFirst().performClick()
        assertIs<PostEvent.Open>(postEvents(events).last())
    }

    @Test
    fun aRepostedCardSaysWhoRepostedItAndOpensThem() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.reposted.copy(eventSink = { events += it })) } }
        onNodeWithText("Reposted by Bob and 1 other").assertExists()
        onNodeWithText("3 likes · 1 comment · 2 reposts · 0 quotes").assertExists()
        onNodeWithTag(TestTags.POST_REPOSTED_BY, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Fixed.BOB, assertIs<PostEvent.OpenReposter>(postEvents(events).last()).reposter.did)
    }

    @Test
    fun theRepostButtonRepostsThePost() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.reposted.copy(eventSink = { events += it })) } }
        onNodeWithTag(TestTags.POST_REPOST, useUnmergedTree = true).performClick()
        assertEquals(fakeRepostedPost().post.uri, assertIs<PostEvent.Repost>(postEvents(events).last()).post.uri)
    }

    @Test
    fun theViewersOwnPostHasNoRepostButton() = runComposeUiTest {
        val own = fakeRepostedPost().let { it.copy(post = it.post.copy(author = fakeProfileBasic(Fixed.ALICE))) }
        val state = FeedSamples.reposted.let { it.copy(content = ContentStates.loaded(persistentListOf(TimelineItem.Repost(own)))) }
        setContent { AppPreview { FeedUi(state) } }
        onNodeWithText("Ledge Line", substring = true).assertExists()
        onNodeWithTag(TestTags.POST_REPOST, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theQuoteButtonQuotesThePost() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.reposted.copy(eventSink = { events += it })) } }
        onNodeWithTag(TestTags.POST_QUOTE, useUnmergedTree = true).performClick()
        assertEquals(fakeRepostedPost().post.uri, assertIs<PostEvent.Quote>(postEvents(events).last()).post.uri)
    }

    @Test
    fun theQuotedPostOpensItAndAQuotedQuotesLinkOpensItsPost() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.quotes.copy(eventSink = { events += it })) } }
        onNodeWithText("Proposes “Wiggly Woods”").assertExists()
        onAllNodesWithTag(TestTags.QUOTED_POST, useUnmergedTree = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(fakePost().uri, assertIs<PostEvent.OpenQuoted>(postEvents(events).last()).uri)
        onNodeWithTag(TestTags.QUOTED_POST_LINK, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(fakePost().uri, assertIs<PostEvent.OpenQuoted>(postEvents(events).last()).uri)
    }

    @Test
    fun anUnavailableQuotedPostOpensNothing() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.quotes.copy(eventSink = { events += it })) } }
        onNodeWithText("Post unavailable").assertExists()
        onNodeWithTag(TestTags.QUOTED_POST_UNAVAILABLE, useUnmergedTree = true).assertHasNoClickAction()
        assertTrue(postEvents(events).none { it is PostEvent.OpenQuoted })
    }

    @Test
    fun someoneElsesQuoteOffersReportNotDelete() = runComposeUiTest {
        setContent { AppPreview { FeedUi(FeedSamples.quotes) } }
        onAllNodesWithContentDescription("More for this post").onFirst().performClick()
        onNodeWithText("Report").assertExists()
        onNodeWithText("Delete quote").assertDoesNotExist()
    }
}
