package io.github.solcott.activitytracker.app.invites

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The invites list, accepting an invite and adding people (feed/activity-invites). */
@OptIn(ExperimentalTestApi::class)
class InvitesUiTest {
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview { content() } }
        checks()
    }

    @Test
    fun theListShowsWhoAddedTheAthleteAndOpensAnInvite() {
        val events = mutableListOf<ActivityInvitesEvent>()
        val state = ActivityInvitesSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        show({ ActivityInvitesUi(state) }) {
            onNodeWithText("Bob added you", substring = true).assertExists()
            onNodeWithText("Gravel loop").assertExists()
            onAllNodesWithText("View")[0].performClick()
        }
        assertEquals(
            ActivityInvitesEvent.Open(ActivityInvitesSamples.all.getValue(SampleKind.LOADED).content.data.pending[0].uri),
            events.last(),
        )
    }

    @Test
    fun anEmptyListSaysSo() =
        show({ ActivityInvitesUi(ActivityInvitesSamples.all.getValue(SampleKind.EMPTY)) }) { onNodeWithText("No invites").assertExists() }

    @Test
    fun acceptingExplainsWhatIsCopiedAndWarnsOfAnOverlap() =
        show({ AcceptInviteUi(AcceptInviteSamples.overlapping) }) {
            onNodeWithText("none of their heart rate", substring = true).assertExists()
            onNodeWithText("You may already have this activity", substring = true).assertExists()
            onNodeWithText("Accept").assertExists()
            onNodeWithText("Decline").assertExists()
        }

    @Test
    fun aGoneInviteSaysSo() =
        show({ AcceptInviteUi(AcceptInviteSamples.all.getValue(SampleKind.EMPTY)) }) {
            onNodeWithText("no longer available", substring = true).assertExists()
        }

    @Test
    fun addingPeopleShowsTheNoticeWhoJoinedAndFollowersToAdd() {
        val events = mutableListOf<AddPeopleEvent>()
        val state = AddPeopleSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        show({ AddPeopleUi(state) }) {
            onNodeWithText("can see this activity's start time, route and speed", substring = true).assertExists()
            onNodeWithText("Joined").assertExists()
            onNodeWithText("Remove").assertExists()
            onNodeWithText("Add").performClick()
        }
        assertEquals(AddPeopleEvent.Add(state.content.data.followers[0].did), events.last())
    }

    @Test
    fun anOldSessionCantAddAndIsAskedToSignInAgain() =
        show({ AddPeopleUi(AddPeopleSamples.all.getValue(SampleKind.LOADED).copy(canInvite = false)) }) {
            onNodeWithText("Add").assertIsNotEnabled()
            // The error says why, and the button below it signs out.
            onNodeWithText("Sign in again").assertExists()
        }
}
