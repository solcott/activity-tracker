package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeGroupPartner
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.togetherWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf

/** The activity page's partners and the owner's leave and rejoin, and the feed card's line (feed/group-activities). */
@OptIn(ExperimentalTestApi::class)
class TogetherWithUiTest {
    @Test
    fun partnersAreListedAndOpenTheirActivity() = runComposeUiTest {
        val events = mutableListOf<ActivityDetailEvent>()
        setContent { AppPreview { TogetherWith(ActivityDetailSamples.page.copy(eventSink = { events += it })) } }
        onNodeWithText("Together with").assertExists()
        onNodeWithText("Carol", substring = true).performClick()
        assertEquals(fakeGroupPartner().uri, assertIs<ActivityDetailEvent.OpenActivity>(events.single()).uri)
        onNodeWithText("Leave group").assertDoesNotExist()
    }

    @Test
    fun theOwnerLeavesAndRejoins() = runComposeUiTest {
        val events = mutableListOf<ActivityDetailEvent>()
        val own = ActivityDetailSamples.page.copy(isOwn = true, ownGroup = OwnGroupState(left = false), eventSink = { events += it })
        var page by mutableStateOf(own)
        setContent { AppPreview { TogetherWith(page) } }
        onNodeWithText("Leave group").performClick()
        page = own.copy(partners = persistentListOf(), ownGroup = OwnGroupState(left = true))
        onNodeWithText("You left the group on this activity.", substring = true).assertExists()
        onNodeWithText("Together with").assertDoesNotExist()
        onNodeWithText("Rejoin group").performClick()
        assertEquals(listOf(ActivityDetailEvent.LeaveGroup, ActivityDetailEvent.RejoinGroup), events)
    }

    @Test
    fun theCardNamesPartners() = runComposeUiTest {
        val bob = fakeGroupPartner(fakeProfileBasic(Fixed.BOB, displayName = "Bob"), "3kb")
        val carol = fakeGroupPartner(fakeProfileBasic(Fixed.CAROL, displayName = "Carol"), "3kc")
        val dan = fakeGroupPartner(fakeProfileBasic(Fixed.ALICE, displayName = "Dan"), "3kd")
        setContent {
            AppPreview {
                Column {
                    Text(togetherWith(persistentListOf(bob)))
                    Text(togetherWith(persistentListOf(bob, carol)))
                    Text(togetherWith(persistentListOf(bob, carol, dan)))
                }
            }
        }
        onNodeWithText("with Bob").assertExists()
        onNodeWithText("with Bob and Carol").assertExists()
        onNodeWithText("with Bob and 2 others").assertExists()
    }
}
