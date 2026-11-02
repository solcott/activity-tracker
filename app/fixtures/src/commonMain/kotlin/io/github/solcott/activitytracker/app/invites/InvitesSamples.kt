package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import kotlinx.collections.immutable.persistentListOf

// Activity invites (feed/activity-invites): an invite from Bob to his ride, and Alice's own ride with people added.

/** [inviter]'s invite of Alice to their ride [rkey]. */
fun fakeInvite(inviter: Did = Fixed.BOB, rkey: String = "3kbride000001", title: String? = "Gravel loop"): InviteView =
    InviteView(
        uri = Fixed.uri(inviter, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey(rkey, Fixed.ALICE)),
        activity = Fixed.uri(inviter, Nsid.FEED_ACTIVITY, rkey),
        inviter = fakeProfileBasic(inviter),
        record = fakeActivityRecord(title = title),
        createdAt = Fixed.CREATED_AT,
    )

/** [invitee] added to Alice's ride, [status] an `InviteStatus` value. */
fun fakeInvitee(invitee: Did = Fixed.BOB, status: String = InviteStatus.PENDING): InviteeView =
    InviteeView(
        uri = Fixed.uri(Fixed.ALICE, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3kaliceride01", invitee)),
        invitee = fakeProfileBasic(invitee),
        status = status,
        createdAt = Fixed.CREATED_AT,
        copy = if (status == InviteStatus.JOINED) Fixed.uri(invitee, Nsid.FEED_ACTIVITY, "3kcopy0000001") else null,
    )

object ActivityInvitesSamples {
    private val content =
        ActivityInvitesContent(
            pending = persistentListOf(fakeInvite(), fakeInvite(Fixed.CAROL, "3kcride000001", title = null)),
            declined = persistentListOf(),
        )

    val all: Map<String, ActivityInvitesState>
        get() =
            mapOf(
                SampleKind.LOADING to ActivityInvitesState(content = ContentStates.loading(ActivityInvitesContent())),
                SampleKind.FAILED to ActivityInvitesState(content = ContentStates.failed(ActivityInvitesContent())),
                SampleKind.EMPTY to ActivityInvitesState(content = ContentStates.loaded(ActivityInvitesContent())),
                SampleKind.LOADED to ActivityInvitesState(content = ContentStates.loaded(content)),
                SampleKind.REFRESHING to ActivityInvitesState(content = ContentStates.refreshing(content)),
                SampleKind.REFRESH_FAILED to ActivityInvitesState(content = ContentStates.refreshFailed(content)),
            )
}

object AcceptInviteSamples {
    private fun state(invite: InviteView?, overlaps: Boolean = false) =
        AcceptInviteState(
            content = ContentStates.loaded(invite),
            title = TextFieldState(invite?.record?.title.orEmpty()),
            visibility = VisibilityFields(),
            overlaps = overlaps,
        )

    /** The athlete already logged part of it. */
    val overlapping: AcceptInviteState
        get() = state(fakeInvite(), overlaps = true)

    val all: Map<String, AcceptInviteState>
        get() =
            mapOf(
                SampleKind.LOADING to AcceptInviteState(content = ContentStates.loading(null)),
                SampleKind.FAILED to AcceptInviteState(content = ContentStates.failed(null)),
                SampleKind.EMPTY to state(null),
                SampleKind.LOADED to state(fakeInvite()),
                SampleKind.REFRESHING to AcceptInviteState(content = ContentStates.refreshing(fakeInvite())),
                SampleKind.REFRESH_FAILED to AcceptInviteState(content = ContentStates.refreshFailed(fakeInvite())),
                "overlapping" to overlapping,
            )
}

object AddPeopleSamples {
    private val content =
        AddPeopleContent(
            invitees = persistentListOf(fakeInvitee(Fixed.BOB, InviteStatus.JOINED)),
            followers = persistentListOf(fakeProfileBasic(Fixed.CAROL)),
        )

    val all: Map<String, AddPeopleState>
        get() =
            mapOf(
                SampleKind.LOADING to AddPeopleState(content = ContentStates.loading(AddPeopleContent())),
                SampleKind.FAILED to AddPeopleState(content = ContentStates.failed(AddPeopleContent())),
                SampleKind.EMPTY to AddPeopleState(content = ContentStates.loaded(AddPeopleContent())),
                SampleKind.LOADED to AddPeopleState(content = ContentStates.loaded(content)),
                SampleKind.REFRESHING to AddPeopleState(content = ContentStates.refreshing(content)),
                SampleKind.REFRESH_FAILED to AddPeopleState(content = ContentStates.refreshFailed(content)),
            )
}
