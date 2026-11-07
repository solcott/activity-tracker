package io.github.solcott.activitytracker.app.post

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The post page (feed/post-page): the card with its counts and like and repost buttons, Show likes and reposts, its comment threads, and an
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
            // On the card, and the comment count again over the threads.
            onAllNodesWithText("3 likes", substring = true).assertCountEquals(1)
            onAllNodesWithText("2 comments", substring = true).assertCountEquals(2)
            onNodeWithTag(TestTags.POST_LIKE).assertExists()
            onNodeWithText("Show likes").assertExists()
            onNodeWithText("The locals have called it that for years.").assertExists()
            onNodeWithText("Exactly why I proposed it.").assertExists()
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
            onNodeWithText("Hide likes").assertExists()
            onNodeWithText("@carol.test", substring = true).assertExists()
        }

    @Test
    fun theRepostButtonSendsARepostAndShowRepostsListsThem() {
        val events = mutableListOf<PostPageEvent>()
        val state = PostPageSamples.all.getValue("loaded").copy(eventSink = { events += it })
        show(state) {
            onAllNodesWithText("1 repost", substring = true).assertCountEquals(1)
            onNodeWithTag(TestTags.POST_REPOST).performClick()
            assertIs<PostEvent.Repost>(assertIs<PostPageEvent.Post>(events.last()).event)
            onNodeWithText("Show reposts").performClick()
            assertEquals(PostPageEvent.ToggleRepostsList, events.last())
        }
    }

    @Test
    fun repostersAreListed() =
        show(PostPageSamples.reposters) {
            onNodeWithText("Hide reposts").assertExists()
            onNodeWithText("@alice.test", substring = true).assertExists()
            onNodeWithText("@carol.test", substring = true).assertExists()
        }

    @Test
    fun anUnavailablePostSaysSo() =
        show(PostPageSamples.notFound) { onNodeWithText("This post isn't available", substring = true).assertExists() }
}
