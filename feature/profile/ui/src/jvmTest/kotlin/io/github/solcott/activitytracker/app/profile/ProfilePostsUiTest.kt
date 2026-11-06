package io.github.solcott.activitytracker.app.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The profile's Posts list (feed/posts). */
@OptIn(ExperimentalTestApi::class)
class ProfilePostsUiTest {
    @Test
    fun choosingPostsAsksForThem() = runComposeUiTest {
        val events = mutableListOf<ProfileEvent>()
        setContent { AppPreview { ProfileUi(ProfileSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("Posts").performClick()
        assertEquals(ProfileEvent.SelectTab(ProfileTab.Posts), events.last())
    }

    @Test
    fun postsListTheAthletesSharedProposals() = runComposeUiTest {
        setContent { AppPreview { ProfileUi(ProfileSamples.posts) } }
        onNodeWithText("Proposes “Wiggly Woods”").assertExists()
        onNodeWithText("Proposes “Rooty Run”").assertExists()
    }

    @Test
    fun noPostsSaysSo() = runComposeUiTest {
        setContent { AppPreview { ProfileUi(ProfileSamples.noPosts) } }
        onNodeWithText("No posts yet").assertExists()
    }
}
