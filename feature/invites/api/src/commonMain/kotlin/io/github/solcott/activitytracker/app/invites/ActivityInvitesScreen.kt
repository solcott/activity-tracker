package io.github.solcott.activitytracker.app.invites

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.Serializable

// Activity invites (feed/activity-invites): the invitee's list of invites, accepting one, and the owner adding people to an activity.

@Serializable
enum class ActivityInvitesTab {
    /** Invites the athlete hasn't answered. */
    Pending,

    /** Invites the athlete declined, which they can still accept. */
    Declined,
}

/** The signed-in athlete's invites to other athletes' activities. */
@CircuitSerializable(SessionScope::class) data object ActivityInvitesScreen : Screen

@Immutable
data class ActivityInvitesState(
    val tab: ActivityInvitesTab = ActivityInvitesTab.Pending,
    /** The invites, loaded (ui/content-loading), with the athlete's declines here laid over them. */
    val content: ContentState<ActivityInvitesContent> = ContentState(ActivityInvitesContent()),
    /** The session can't read or write declines, so every invite shows as pending; declining needs the athlete to sign in again. */
    val needsSignIn: Boolean = false,
    /** Invites whose decline is being written. */
    val answering: ImmutableSet<AtUri> = persistentSetOf(),
    /** Why declining failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (ActivityInvitesEvent) -> Unit = {},
) : CircuitUiState {
    val pending: ImmutableList<InviteView>
        get() = content.data.pending

    val declined: ImmutableList<InviteView>
        get() = content.data.declined

    /** The invites the chosen [tab] lists. */
    val invites: ImmutableList<InviteView>
        get() = if (tab == ActivityInvitesTab.Pending) pending else declined

    /** Whether the listed invites can be declined: pending ones can while the session can write declines, and declined ones already are. */
    val offersDecline: Boolean
        get() = tab == ActivityInvitesTab.Pending && !needsSignIn

    /** Whether the note that only the athlete knows they declined shows: over a Declined list that lists an invite. */
    val showsDeclinedNote: Boolean
        get() = tab == ActivityInvitesTab.Declined && declined.isNotEmpty()

    /** Why declining failed, or that the session must sign in again to decline, over the invites; null for neither. */
    val notice: SignInAgainNotice?
        get() = SignInAgainNotice.of(error, needsSignIn)

    /** Whether [invite]'s buttons can be used now: not while its decline is written. */
    fun canAnswer(invite: InviteView): Boolean = invite.uri !in answering
}

/** What the invites list says, chosen here for the shared and the native UI (dev/shared-display-text). */
object ActivityInvitesTexts {
    /** The tab's label. */
    fun tab(tab: ActivityInvitesTab): AppText =
        AppText.named(
            when (tab) {
                ActivityInvitesTab.Pending -> "activity_invites_tab_pending"
                ActivityInvitesTab.Declined -> "activity_invites_tab_declined"
            },
        )

    /** What the tab says when it lists nothing. */
    fun empty(tab: ActivityInvitesTab): AppText =
        AppText.named(
            when (tab) {
                ActivityInvitesTab.Pending -> "activity_invites_none"
                ActivityInvitesTab.Declined -> "activity_invites_none_declined"
            },
        )

    /** The invite's activity by its title, or by its type when it has none. */
    fun title(invite: InviteView): AppText =
        invite.record.title?.let(AppText::written) ?: LabelText.activityType(ActivityType(invite.record.sport, invite.record.subSport))

    /** Who added the athlete: "Bob added you". */
    fun from(invite: InviteView): AppText = AppText.named("activity_invites_from", AppText.written(invite.inviter.shownName))

    /** The activity's distance in [units] and its time, or its time alone when it has no distance. */
    fun summary(invite: InviteView, units: UnitSystem): AppText {
        val time = AppText.written(StatText.duration(invite.record.elapsedTime))
        val distance = invite.record.distance ?: return time
        return AppText.named("activity_invites_summary", StatText.distance(distance.toDouble(), units), time)
    }
}

@Immutable
data class ActivityInvitesContent(
    val pending: ImmutableList<InviteView> = persistentListOf(),
    val declined: ImmutableList<InviteView> = persistentListOf(),
)

sealed interface ActivityInvitesEvent {
    data object Back : ActivityInvitesEvent

    data class SelectTab(val tab: ActivityInvitesTab) : ActivityInvitesEvent

    /** Opens [invite] to accept or decline it. */
    data class Open(val invite: AtUri) : ActivityInvitesEvent

    /** Declines [invite], privately. */
    data class Decline(val invite: AtUri) : ActivityInvitesEvent

    data object Retry : ActivityInvitesEvent

    /** Signs out, so the athlete can sign in again with access to declines ([ActivityInvitesState.needsSignIn]). */
    data object SignInAgain : ActivityInvitesEvent
}

/** One invite naming the athlete: what accepting copies, its visibility, and Accept or Decline. [invite] is the invite's AT-URI. */
@CircuitSerializable(SessionScope::class) data class AcceptInviteScreen(val invite: String) : Screen

@Immutable
data class AcceptInviteState(
    /** The invite and what it lets the athlete copy; null when it's gone (cancelled, lapsed or accepted). */
    val content: ContentState<InviteView?> = ContentState(null),
    /** The copy's title, prefilled from the original's. */
    val title: TextFieldState = textFieldStateOf(),
    /** The copy's visibility and its categories', prefilled from the athlete's defaults. */
    val visibility: VisibilityFields = VisibilityFields(),
    /** The athlete already has an activity overlapping this one: they may have recorded part of it. */
    val overlaps: Boolean = false,
    val accepting: Boolean = false,
    val declining: Boolean = false,
    /** Whether this session can decline: one signed in before declines were in its grant can't. */
    val canDecline: Boolean = true,
    /** Why accepting or declining failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (AcceptInviteEvent) -> Unit = {},
) : CircuitUiState

sealed interface AcceptInviteEvent {
    data object Back : AcceptInviteEvent

    /** Publishes the athlete's own copy and goes back. */
    data object Accept : AcceptInviteEvent

    /** Declines the invite, privately, and goes back. */
    data object Decline : AcceptInviteEvent

    data object Retry : AcceptInviteEvent

    data object SignInAgain : AcceptInviteEvent
}

/** The followers the athlete added to their own [activity] (its AT-URI), and adding more. */
@CircuitSerializable(SessionScope::class) data class AddPeopleScreen(val activity: String) : Screen

@Immutable
data class AddPeopleState(
    val content: ContentState<AddPeopleContent> = ContentState(AddPeopleContent()),
    /** Whether this session can add people: one signed in before invites were in its grant can't, and is asked to sign in again. */
    val canInvite: Boolean = true,
    /** Accounts being added or removed. */
    val busy: ImmutableSet<Did> = persistentSetOf(),
    /** Why adding or removing failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (AddPeopleEvent) -> Unit = {},
) : CircuitUiState {
    val invitees: ImmutableList<InviteeView>
        get() = content.data.invitees

    val followers: ImmutableList<ProfileViewBasic>
        get() = content.data.followers

    /** No one more can be added. */
    val full: Boolean
        get() = content.data.full

    /** Why adding or removing failed, or that the session must sign in again to add people, over the lists; null for neither. */
    val notice: SignInAgainNotice?
        get() = SignInAgainNotice.of(error, needsSignIn = !canInvite)

    /** Whether [follower] can be added now: while the session can invite, the activity isn't full and they aren't being added. */
    fun canAdd(follower: ProfileViewBasic): Boolean = canInvite && !full && follower.did !in busy

    /** Whether [invitee] can be cancelled or removed now: while the session can invite and they aren't being removed. */
    fun canRemove(invitee: InviteeView): Boolean = canInvite && invitee.invitee.did !in busy
}

/** What Add people says, chosen here for the shared and the native UI (dev/shared-display-text). */
object AddPeopleTexts {
    /** Whether [invitee] joined or is still pending, a declined invite included. */
    fun status(invitee: InviteeView): AppText =
        AppText.named(if (invitee.status == InviteStatus.JOINED) "add_people_joined" else "add_people_pending")

    /** [invitee]'s button: Remove from the group once joined, Cancel the invite before. */
    fun remove(invitee: InviteeView): AppText =
        AppText.named(if (invitee.status == InviteStatus.JOINED) "add_people_remove" else "add_people_cancel")
}

@Immutable
data class AddPeopleContent(
    /** Who was added, oldest first, each Pending or Joined. */
    val invitees: ImmutableList<InviteeView> = persistentListOf(),
    /** The athlete's followers not added yet. */
    val followers: ImmutableList<ProfileViewBasic> = persistentListOf(),
) {
    /** No one more can be added. */
    val full: Boolean
        get() = invitees.size >= ActivityInviteRecord.MAX
}

sealed interface AddPeopleEvent {
    data object Back : AddPeopleEvent

    data class Add(val did: Did) : AddPeopleEvent

    /** Cancels a pending invite, or removes a joined invitee from the group; their copy stays theirs. */
    data class Remove(val invitee: InviteeView) : AddPeopleEvent

    data object Retry : AddPeopleEvent

    data object SignInAgain : AddPeopleEvent
}
