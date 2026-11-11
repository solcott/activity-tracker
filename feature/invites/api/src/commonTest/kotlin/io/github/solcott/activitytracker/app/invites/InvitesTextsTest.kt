package io.github.solcott.activitytracker.app.invites

import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** What the invites list and Add people say and offer (feed/activity-invites, dev/shared-display-text). */
class InvitesTextsTest {
    private val at = Instant.fromEpochSeconds(1_750_000_000)

    private fun account(name: String) =
        ProfileViewBasic(Did("did:plc:$name"), Handle("$name.test"), name.replaceFirstChar { it.uppercase() }, null)

    private val bob = account("bob")
    private val carol = account("carol")

    private fun invite(from: ProfileViewBasic, title: String? = "Gravel loop", distance: Long? = 32_400) =
        InviteView(
            uri = AtUri("at://${from.did.value}/app.activitytracker.feed.activityInvite/${from.handle.value}"),
            activity = AtUri("at://${from.did.value}/app.activitytracker.feed.activity/3kbride000001"),
            inviter = from,
            record =
                ActivityRecord(sport = "ride", startedAt = at, elapsedTime = 3_900, createdAt = at, title = title, distance = distance),
            createdAt = at,
        )

    private fun invitee(who: ProfileViewBasic, status: String) =
        InviteeView(AtUri("at://did:plc:alice/x/${ActivityInviteRecord.rkey("3kaliceride01", who.did)}"), who, status, at)

    private val fromBob = invite(bob)
    private val fromCarol = invite(carol, title = null)

    private fun invites(
        tab: ActivityInvitesTab,
        pending: List<InviteView> = listOf(fromBob),
        declined: List<InviteView> = listOf(fromCarol),
    ) =
        ActivityInvitesState(
            tab = tab,
            content = ContentState(ActivityInvitesContent(pending.toImmutableList(), declined.toImmutableList())),
        )

    @Test
    fun eachTabHasItsWordsAndInvites() {
        assertEquals(AppText.named("activity_invites_tab_pending"), ActivityInvitesTexts.tab(ActivityInvitesTab.Pending))
        assertEquals(AppText.named("activity_invites_tab_declined"), ActivityInvitesTexts.tab(ActivityInvitesTab.Declined))
        assertEquals(AppText.named("activity_invites_none"), ActivityInvitesTexts.empty(ActivityInvitesTab.Pending))
        assertEquals(AppText.named("activity_invites_none_declined"), ActivityInvitesTexts.empty(ActivityInvitesTab.Declined))
        assertEquals(listOf(fromBob), invites(ActivityInvitesTab.Pending).invites)
        assertEquals(listOf(fromCarol), invites(ActivityInvitesTab.Declined).invites)
    }

    @Test
    fun anInviteReadsByItsTitleOrItsType() {
        assertEquals(AppText.written("Gravel loop"), ActivityInvitesTexts.title(fromBob))
        assertEquals(LabelText.activityType(ActivityType("ride", null)), ActivityInvitesTexts.title(fromCarol))
        assertEquals(AppText.named("activity_invites_from", AppText.written("Bob")), ActivityInvitesTexts.from(fromBob))
    }

    @Test
    fun anInvitesSummaryIsItsDistanceAndTimeOrItsTimeAlone() {
        assertEquals(
            AppText.named("activity_invites_summary", StatText.distance(32_400.0, UnitSystem.Imperial), AppText.written("1:05:00")),
            ActivityInvitesTexts.summary(fromBob, UnitSystem.Imperial),
        )
        assertEquals(AppText.written("1:05:00"), ActivityInvitesTexts.summary(invite(bob, distance = null), UnitSystem.Metric))
    }

    @Test
    fun onlyPendingInvitesCanBeDeclinedAndOnlyWhileTheSessionCan() {
        assertTrue(invites(ActivityInvitesTab.Pending).offersDecline)
        assertFalse(invites(ActivityInvitesTab.Declined).offersDecline)
        assertFalse(invites(ActivityInvitesTab.Pending).copy(needsSignIn = true).offersDecline)
    }

    @Test
    fun theDeclinedNoteShowsOverADeclinedListThatListsAnInvite() {
        assertTrue(invites(ActivityInvitesTab.Declined).showsDeclinedNote)
        assertFalse(invites(ActivityInvitesTab.Declined, declined = emptyList()).showsDeclinedNote)
        assertFalse(invites(ActivityInvitesTab.Pending).showsDeclinedNote)
    }

    @Test
    fun anInviteCantBeAnsweredWhileItsDeclineIsWritten() {
        val state = invites(ActivityInvitesTab.Pending, pending = listOf(fromBob, fromCarol)).copy(answering = persistentSetOf(fromBob.uri))
        assertFalse(state.canAnswer(fromBob))
        assertTrue(state.canAnswer(fromCarol))
    }

    @Test
    fun theInvitesNoticeIsTheSharedRules() {
        val failed = UiError(UiError.Kind.AnswerActivityInvite, DataError.Network)
        assertNull(invites(ActivityInvitesTab.Pending).notice)
        assertEquals(SignInAgainNotice.of(null, true), invites(ActivityInvitesTab.Pending).copy(needsSignIn = true).notice)
        assertEquals(SignInAgainNotice(failed, false), invites(ActivityInvitesTab.Pending).copy(error = failed).notice)
    }

    private fun people(
        invitees: List<InviteeView> = listOf(invitee(bob, InviteStatus.PENDING)),
        followers: List<ProfileViewBasic> = listOf(carol),
    ) = AddPeopleState(content = ContentState(AddPeopleContent(invitees.toImmutableList(), followers.toImmutableList())))

    @Test
    fun anInviteeReadsAsPendingWithCancelOrJoinedWithRemove() {
        val pending = invitee(bob, InviteStatus.PENDING)
        val joined = invitee(bob, InviteStatus.JOINED)
        assertEquals(AppText.named("add_people_pending"), AddPeopleTexts.status(pending))
        assertEquals(AppText.named("add_people_cancel"), AddPeopleTexts.remove(pending))
        assertEquals(AppText.named("add_people_joined"), AddPeopleTexts.status(joined))
        assertEquals(AppText.named("add_people_remove"), AddPeopleTexts.remove(joined))
    }

    @Test
    fun theListsAreTheContents() {
        val state = people()
        assertEquals(listOf(invitee(bob, InviteStatus.PENDING)), state.invitees)
        assertEquals(listOf(carol), state.followers)
        assertFalse(state.full)
    }

    @Test
    fun anAccountsButtonWaitsWhileItIsAddedOrRemoved() {
        val bobAdded = invitee(bob, InviteStatus.PENDING)
        val state = people(followers = listOf(carol, account("dave"))).copy(busy = persistentSetOf(bob.did, carol.did))
        assertFalse(state.canRemove(bobAdded))
        assertFalse(state.canAdd(carol))
        assertTrue(state.canAdd(account("dave")))
        assertTrue(people().canRemove(bobAdded))
    }

    @Test
    fun aFullActivityTakesNoOneMore() {
        val many = (1..ActivityInviteRecord.MAX).map { invitee(account("friend$it"), InviteStatus.PENDING) }
        val state = people(invitees = many)
        assertTrue(state.full)
        assertFalse(state.canAdd(carol))
        assertTrue(state.canRemove(many.first()))
    }

    @Test
    fun aSessionThatCantInviteOffersNothingAndSaysToSignInAgain() {
        val state = people().copy(canInvite = false)
        assertFalse(state.canAdd(carol))
        assertFalse(state.canRemove(invitee(bob, InviteStatus.PENDING)))
        assertEquals(SignInAgainNotice.of(null, true), state.notice)
        assertNull(people().notice)
        val failed = UiError(UiError.Kind.UpdateInvitees, DataError.Network)
        assertEquals(SignInAgainNotice(failed, false), people().copy(error = failed).notice)
    }

    @Test
    fun emptyListsReadEmpty() {
        val state = AddPeopleState(content = ContentState(AddPeopleContent(persistentListOf(), persistentListOf())))
        assertTrue(state.invitees.isEmpty() && state.followers.isEmpty())
    }
}
