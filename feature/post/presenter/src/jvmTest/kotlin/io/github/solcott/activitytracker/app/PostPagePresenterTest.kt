package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.post.PostPage
import io.github.solcott.activitytracker.app.post.PostPageEvent
import io.github.solcott.activitytracker.app.post.PostPagePresenter
import io.github.solcott.activitytracker.app.post.PostPageScreen
import io.github.solcott.activitytracker.app.post.PostPageState
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.rememberPostEventSink
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The post page's presenter (feed/post-page): loading the post, NotFound, retry, liking, commenting and replying, and hiding. */
class PostPagePresenterTest : ActivityTestBase() {
    private val postUri = "at://did:plc:bob/${Nsid.FEED_POST}/3kpost"
    private val screen = PostPageScreen(postUri)

    private fun post(visibility: String = "followers", liked: Boolean = false, author: String = bob) =
        """{"${'$'}type":"${Nsid.FEED_DEFS}#postView","uri":"$postUri","cid":"bafypost","author":$author,"text":"Why",""" +
            """"visibility":"$visibility","embed":{"${'$'}type":"${Nsid.FEED_DEFS}#postProposalEmbedView",""" +
            """"proposal":{"uri":"at://did:plc:bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kpost","cid":"bafyq"},"name":"Wiggly Woods",""" +
            """"status":"open","openedAt":"2026-11-05T10:00:00Z","up":1,"down":0,""" +
            """"segment":{"uri":"at://did:plc:importer/${Nsid.FEED_SEGMENT}/osm-1","name":"Trail 214",""" +
            """"sport":"ride","distanceMeters":1200}},""" +
            """"createdAt":"2026-11-05T10:00:00Z","indexedAt":"2026-11-05T10:00:00Z",""" +
            """"likeCount":${if (liked) 1 else 0},"commentCount":${commentsFromAppView.size},""" +
            """"viewer":{${if (liked) """"like":"at://did:plc:alice/${Nsid.FEED_LIKE}/3klike"""" else ""}}}"""

    private fun presenterOf(r: Repos, navigator: FakeNavigator = FakeNavigator(screen)): @Composable () -> PostPageState = {
        PostPagePresenter(screen, navigator, r.feed, r.activities, r.profiles, r.names, r.blocks, r.reports)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<PostPageState>.awaitPage(done: (PostPage) -> Boolean = { true }): PostPage =
        awaitUntil { state -> state.content.data?.let(done) == true }.content.data!!

    private suspend fun Repos.queued(collection: String) =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == collection }
            .map { it.space?.substringAfterLast('/') to it.recordJson.orEmpty() }

    @Test
    fun loadsThePostWithItsComments() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post()
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val page = awaitPage { it.comments.paging.refresh is LoadStatus.Idle && it.comments.paging.items.isNotEmpty() }
            assertEquals("Why", page.post.text)
            assertEquals(1, page.post.commentCount)
            assertEquals(commentUri("did:plc:carol", "3kc1"), page.comments.paging.items.single().uri.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPostThatIsntServedIsNotFoundAndARetryLoadsIt() = runTest {
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val missing = awaitUntil { it.notFound }
            postFromAppView = post()
            missing.eventSink(PostPageEvent.Retry)
            awaitPage()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun likingAFollowersPostQueuesItInTheInteractionsSpaceAndShowsAtOnce() = runTest {
        postFromAppView = post()
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val state = awaitUntil { it.content.data != null }
            state.eventSink(PostPageEvent.Post(PostEvent.Like(state.content.data!!.post)))
            val liked = awaitPage { it.post.viewer?.like != null }
            assertEquals(1, liked.post.likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCommentIsWrittenForThePostsAudienceAndCountedAtOnce() = runTest {
        postFromAppView = post(visibility = "followers")
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val page = awaitPage { it.comments.paging.refresh is LoadStatus.Idle }
            page.comments.draft.setTextAndPlaceCursorAtEnd("Agreed")
            page.comments.eventSink(CommentEvent.Send)
            page.comments.eventSink(CommentEvent.Send)
            val sent = awaitPage { it.comments.sent.isNotEmpty() }
            assertEquals(1, sent.post.commentCount)
            val queued = r.queued(Nsid.FEED_COMMENT).single()
            assertEquals("interactions", queued.first)
            assertTrue("\"audience\":\"followers\"" in queued.second, queued.second)
            assertTrue(postUri in queued.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aReplyAnswersTheListedComment() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post(visibility = "followers")
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val page = awaitPage { it.comments.paging.items.isNotEmpty() }
            page.comments.eventSink(CommentEvent.ReplyTo(page.comments.paging.items.single().uri))
            val replying = awaitPage { it.comments.replyingTo != null }
            replying.comments.draft.setTextAndPlaceCursorAtEnd("Me too")
            replying.comments.eventSink(CommentEvent.Send)
            awaitPage { it.comments.paging.items.single().sentReplies.isNotEmpty() }
            val queued = r.queued(Nsid.FEED_COMMENT).single()
            assertEquals("interactions", queued.first)
            assertTrue("\"reply\"" in queued.second && commentUri("did:plc:carol", "3kc1") in queued.second, queued.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theAuthorHidesACommentOnTheirPost() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post(visibility = "public", author = staleAlice)
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val page = awaitPage { it.comments.paging.items.isNotEmpty() }
            assertTrue(page.comments.isOwn)
            page.comments.eventSink(CommentEvent.Hide(page.comments.paging.items.single().uri))
            val hidden = awaitPage { it.comments.paging.items.single().root?.hidden == true }
            assertEquals(0, hidden.post.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theSegmentOnTheCardOpensItAndThePostItselfDoesNothing() = runTest {
        postFromAppView = post()
        val r = repos()
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenterOf(r, navigator)) {
            val state = awaitUntil { it.content.data != null }
            val post = state.content.data!!.post
            state.eventSink(PostPageEvent.Post(PostEvent.OpenPost(post)))
            state.eventSink(PostPageEvent.Post(PostEvent.Open(post)))
            assertIs<SegmentScreen>(navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theTimelinesPostSinkOpensThePostPage() = runTest {
        val r = repos()
        // The timeline, say: any screen but the post page itself.
        val navigator = FakeNavigator(PostPageScreen("at://did:plc:carol/${Nsid.FEED_POST}/3kother"))
        val view = AtJson.decodeFromString(PostView.serializer(), post())
        presenterTestOf({ SinkState(rememberPostEventSink(navigator, r.names, r.activities, Reporting()) {}) }) {
            awaitItem().sink(PostEvent.OpenPost(view))
            assertEquals(PostPageScreen(postUri), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** The timeline's post sink, as a presenter's state, to drive it as the feed does. */
private data class SinkState(val sink: (PostEvent) -> Unit) : CircuitUiState
