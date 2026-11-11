package io.github.solcott.activitytracker.app.invites

import io.github.solcott.activitytracker.app.UiError
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
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

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

    /**
     * What [all]'s content-loading states don't show: the Declined tab, listing bob's invite or nothing; bob's decline being written and
     * the last one failed; a session that must sign in again to decline; and an activity with no distance. Kept apart from [all], whose
     * keys are the content-loading conformance set.
     */
    val variants: Map<String, ActivityInvitesState>
        get() =
            mapOf(
                "declined" to
                    ActivityInvitesState(
                        tab = ActivityInvitesTab.Declined,
                        content =
                            ContentStates.loaded(
                                ActivityInvitesContent(
                                    content.pending.drop(1).toImmutableList(),
                                    content.pending.take(1).toImmutableList(),
                                ),
                            ),
                    ),
                "declinedEmpty" to ActivityInvitesState(tab = ActivityInvitesTab.Declined, content = ContentStates.loaded(content)),
                "answering" to
                    ActivityInvitesState(
                        content = ContentStates.loaded(content),
                        answering = persistentSetOf(content.pending.first().uri),
                        error = UiError(UiError.Kind.AnswerActivityInvite, DataError.Network),
                    ),
                "needsSignIn" to ActivityInvitesState(content = ContentStates.loaded(content), needsSignIn = true),
                "noDistance" to
                    ActivityInvitesState(
                        content =
                            ContentStates.loaded(
                                ActivityInvitesContent(
                                    pending =
                                        persistentListOf(
                                            fakeInvite().copy(record = fakeActivityRecord(title = "Trainer ride", distance = null)),
                                        ),
                                ),
                            ),
                    ),
            )
}

object AcceptInviteSamples {
    private fun state(invite: InviteView?, overlaps: Boolean = false) =
        AcceptInviteState(
            content = ContentStates.loaded(invite),
            title = textFieldStateOf(invite?.record?.title.orEmpty()),
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

    /**
     * What [all]'s content-loading states don't show: bob pending while carol is being added and the last add failed; bob joined and being
     * removed; a full activity, with a follower who can't be added; no followers; and a session that must sign in again to add people. Kept
     * apart from [all], whose keys are the content-loading conformance set.
     */
    val variants: Map<String, AddPeopleState>
        get() =
            mapOf(
                "pending" to
                    AddPeopleState(
                        content =
                            ContentStates.loaded(content.copy(invitees = persistentListOf(fakeInvitee(Fixed.BOB, InviteStatus.PENDING)))),
                        busy = persistentSetOf(Fixed.CAROL),
                        error = UiError(UiError.Kind.UpdateInvitees, DataError.Network),
                    ),
                "removing" to AddPeopleState(content = ContentStates.loaded(content), busy = persistentSetOf(Fixed.BOB)),
                "full" to
                    AddPeopleState(
                        content =
                            ContentStates.loaded(
                                AddPeopleContent(
                                    invitees =
                                        (listOf(fakeInvitee(Fixed.BOB, InviteStatus.JOINED), fakeInvitee(Fixed.CAROL)) +
                                                (3..ActivityInviteRecord.MAX).map { fakeInvitee(Did("did:plc:rider$it")) })
                                            .toImmutableList(),
                                    followers = persistentListOf(fakeProfileBasic(DAVE)),
                                ),
                            ),
                    ),
                "noFollowers" to AddPeopleState(content = ContentStates.loaded(content.copy(followers = persistentListOf()))),
                "needsSignIn" to AddPeopleState(content = ContentStates.loaded(content), canInvite = false),
            )

    private val DAVE = Did("did:plc:dave")
}
