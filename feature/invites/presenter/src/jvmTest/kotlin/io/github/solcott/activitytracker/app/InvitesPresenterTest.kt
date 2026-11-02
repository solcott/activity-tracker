package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.invites.AcceptInviteEvent
import io.github.solcott.activitytracker.app.invites.AcceptInvitePresenter
import io.github.solcott.activitytracker.app.invites.AcceptInviteScreen
import io.github.solcott.activitytracker.app.invites.ActivityInvitesEvent
import io.github.solcott.activitytracker.app.invites.ActivityInvitesPresenter
import io.github.solcott.activitytracker.app.invites.ActivityInvitesScreen
import io.github.solcott.activitytracker.app.invites.AddPeopleEvent
import io.github.solcott.activitytracker.app.invites.AddPeoplePresenter
import io.github.solcott.activitytracker.app.invites.AddPeopleScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The invites list, accepting an invite, and adding people (feed/activity-invites). */
class InvitesPresenterTest {
    private val fixture = SessionTestFixture()
    private val bob = Did("did:plc:bob")
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val bobsRide = AtUri.of(bob, Nsid.FEED_ACTIVITY, "3kbride000001")
    private val invite =
        InviteView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3kbride000001", fixture.did)),
            activity = bobsRide,
            inviter = ProfileViewBasic(bob, Handle("bob.test"), displayName = "Bob"),
            record = ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 3600, createdAt = start, title = "Gravel loop"),
            createdAt = start,
        )
    private val ownRide = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3kaliceride01")

    @Test
    fun decliningMovesAnInviteToDeclined() = runTest {
        fixture.invitesServed = listOf(invite)
        val repos = fixture.repos()
        presenterTestOf({ ActivityInvitesPresenter(FakeNavigator(ActivityInvitesScreen), repos.invites, repos.auth) }) {
            val loaded = awaitUntil { it.content.data.pending.isNotEmpty() }
            loaded.eventSink(ActivityInvitesEvent.Decline(invite.uri))
            val declined = awaitUntil { it.content.data.declined.isNotEmpty() }
            assertEquals(emptyList(), declined.content.data.pending)
            assertEquals(listOf(invite.uri), declined.content.data.declined.map { it.uri })
        }
    }

    @Test
    fun openingAnInviteGoesToAcceptIt() = runTest {
        fixture.invitesServed = listOf(invite)
        val repos = fixture.repos()
        val navigator = FakeNavigator(ActivityInvitesScreen)
        presenterTestOf({ ActivityInvitesPresenter(navigator, repos.invites, repos.auth) }) {
            awaitUntil { it.content.data.pending.isNotEmpty() }.eventSink(ActivityInvitesEvent.Open(invite.uri))
            assertEquals(AcceptInviteScreen(invite.uri.value), navigator.awaitNextScreen())
        }
    }

    @Test
    fun acceptingPrefillsTheTitlePublishesTheCopyAndGoesBack() = runTest {
        fixture.inviteServed[invite.uri.value] = GetActivityInvite.Output(invite)
        val repos = fixture.repos()
        val screen = AcceptInviteScreen(invite.uri.value)
        val navigator = FakeNavigator(screen)
        presenterTestOf({ AcceptInvitePresenter(screen, navigator, repos.invites, repos.defaults, repos.auth) }) {
            val loaded = awaitUntil { it.content.data != null && it.title.text.isNotEmpty() }
            assertEquals("Gravel loop", loaded.title.text.toString())
            loaded.eventSink(AcceptInviteEvent.Accept)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aGoneInviteFailsToLoad() = runTest {
        val repos = fixture.repos()
        val screen = AcceptInviteScreen(invite.uri.value)
        presenterTestOf({ AcceptInvitePresenter(screen, FakeNavigator(screen), repos.invites, repos.defaults, repos.auth) }) {
            assertNotNull(awaitUntil { it.content.status is io.github.solcott.uistate.LoadStatus.Failed })
        }
    }

    @Test
    fun addingAFollowerListsThemPendingAndAnOfflineAddKeepsTheList() = runTest {
        fixture.followers = listOf("did:plc:carol", "did:plc:dave")
        val repos = fixture.repos()
        val screen = AddPeopleScreen(ownRide.value)
        presenterTestOf({ AddPeoplePresenter(screen, FakeNavigator(screen), repos.invites, repos.auth) }) {
            val loaded = awaitUntil { it.content.data.followers.size == 2 }
            loaded.eventSink(AddPeopleEvent.Add(Did("did:plc:carol")))
            val added = awaitUntil { it.content.data.invitees.isNotEmpty() }
            assertEquals(InviteStatus.PENDING, added.content.data.invitees.single().status)
            assertEquals(listOf(Did("did:plc:dave")), added.content.data.followers.map { it.did })

            fixture.offline = true
            added.eventSink(AddPeopleEvent.Add(Did("did:plc:dave")))
            val failed = awaitUntil { it.error != null }
            assertEquals(listOf(Did("did:plc:dave")), failed.content.data.followers.map { it.did })
            assertTrue(failed.content.data.invitees.size == 1)
        }
    }

    @Test
    fun removingAPendingInviteeOffersThemAgain() = runTest {
        fixture.followers = listOf("did:plc:carol")
        val repos = fixture.repos()
        val screen = AddPeopleScreen(ownRide.value)
        presenterTestOf({ AddPeoplePresenter(screen, FakeNavigator(screen), repos.invites, repos.auth) }) {
            awaitUntil { it.content.data.followers.size == 1 }.eventSink(AddPeopleEvent.Add(Did("did:plc:carol")))
            val added = awaitUntil { it.content.data.invitees.isNotEmpty() }
            added.eventSink(AddPeopleEvent.Remove(added.content.data.invitees.single()))
            val removed = awaitUntil { it.content.data.invitees.isEmpty() }
            assertEquals(listOf(Did("did:plc:carol")), removed.content.data.followers.map { it.did })
        }
    }
}
