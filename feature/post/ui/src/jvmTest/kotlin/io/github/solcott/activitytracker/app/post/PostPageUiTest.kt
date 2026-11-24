package io.github.solcott.activitytracker.app.post

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewEvent
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The post page (feed/post-page): the card with its counts and like and repost buttons, Show likes and reposts, its comments block, and an
 * unavailable post.
 */
@OptIn(ExperimentalTestApi::class)
class PostPageUiTest {
    private fun show(state: PostPageState, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview { PostPageUi(state) } }
        checks()
    }

    @Test
    fun thePostShowsItsCountsLikeButtonAndComments() =
        show(PostPageSamples.all.getValue("loaded")) {
            onNodeWithText("Everyone at the trailhead already calls it this.").assertExists()
            // The counts once each, by icon in the social card's row (the card itself keeps only its buttons).
            onNodeWithContentDescription("3 likes").assertExists()
            onNodeWithContentDescription("2 comments").assertExists()
            onAllNodesWithText("2 comments", substring = true).assertCountEquals(0)
            onNodeWithTag(TestTags.POST_LIKE).assertExists()
            onNode(clickLabelled("Show likes")).assertExists()
            // The block previews top-level comments only, and adds one on the Comments screen.
            onNodeWithText("The locals have called it that for years.").assertExists()
            onNodeWithText("Exactly why I proposed it.").assertDoesNotExist()
            onNodeWithText("Add a comment").assertExists()
        }

    @Test
    fun theCommentsBlockOpensTheCommentsScreen() {
        val events = mutableListOf<CommentsPreviewEvent>()
        val page = PostPageSamples.page.let { it.copy(comments = it.comments.copy(eventSink = { e -> events += e })) }
        show(PostPageState(ContentStates.loaded(page), eventSink = {})) {
            onNodeWithText("View all").performClick()
            assertEquals(CommentsPreviewEvent.OpenAll, events.last())
            onNodeWithContentDescription("2 comments").performClick()
            assertEquals(CommentsPreviewEvent.OpenAll, events.last())
            onNodeWithText("Add a comment").performClick()
            assertEquals(CommentsPreviewEvent.Compose, events.last())
        }
    }

    @Test
    fun theLikeButtonSendsALike() {
        val events = mutableListOf<PostPageEvent>()
        val state = PostPageSamples.all.getValue("loaded").copy(eventSink = { events += it })
        show(state) {
            onNodeWithTag(TestTags.POST_LIKE).performClick()
            assertIs<PostEvent.Like>(assertIs<PostPageEvent.Post>(events.single()).event)
        }
    }

    @Test
    fun likersAreListed() =
        show(PostPageSamples.likers) {
            onNode(clickLabelled("Hide likes")).assertExists()
            onNodeWithText("@carol.test", substring = true).assertExists()
        }

    @Test
    fun theRepostButtonSendsARepostAndShowRepostsListsThem() {
        val events = mutableListOf<PostPageEvent>()
        val state = PostPageSamples.all.getValue("loaded").copy(eventSink = { events += it })
        show(state) {
            onNodeWithContentDescription("1 repost").assertExists()
            onNodeWithTag(TestTags.POST_REPOST).performClick()
            assertIs<PostEvent.Repost>(assertIs<PostPageEvent.Post>(events.last()).event)
            onNode(clickLabelled("Show reposts")).performClick()
            assertEquals(PostPageEvent.ToggleRepostsList, events.last())
        }
    }

    @Test
    fun repostersAreListed() =
        show(PostPageSamples.reposters) {
            onNode(clickLabelled("Hide reposts")).assertExists()
            onNodeWithText("@alice.test", substring = true).assertExists()
            onNodeWithText("@carol.test", substring = true).assertExists()
        }

    @Test
    fun anUnavailablePostSaysSo() =
        show(PostPageSamples.notFound) { onNodeWithText("This post isn't available", substring = true).assertExists() }

    @Test
    fun theQuoteCountOpensTheQuotes() {
        val events = mutableListOf<PostPageEvent>()
        show(PostPageSamples.all.getValue("loaded").copy(eventSink = { events += it })) {
            onNodeWithTag(TestTags.POST_QUOTE_COUNT).assert(clickLabelled("Show quotes")).performClick()
            assertEquals(PostPageEvent.OpenQuotes, events.last())
        }
    }

    @Test
    fun theirOwnQuoteOffersDelete() {
        val events = mutableListOf<PostPageEvent>()
        show(PostPageSamples.ownQuote.copy(eventSink = { events += it })) {
            onNodeWithContentDescription("More for this post").performClick()
            onNodeWithText("Delete quote").performClick()
            assertIs<PostEvent.Delete>(assertIs<PostPageEvent.Post>(events.last()).event)
        }
    }

    /** A button whose tap says [label] ("Show likes"): the counts row's counts, which are read by their totals. */
    private fun clickLabelled(label: String) =
        SemanticsMatcher("click labelled $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
}
