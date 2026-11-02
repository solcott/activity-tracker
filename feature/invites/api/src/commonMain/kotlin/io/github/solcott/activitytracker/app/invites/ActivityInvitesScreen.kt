package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
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
) : CircuitUiState

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
    val title: TextFieldState = TextFieldState(),
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
) : CircuitUiState

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
