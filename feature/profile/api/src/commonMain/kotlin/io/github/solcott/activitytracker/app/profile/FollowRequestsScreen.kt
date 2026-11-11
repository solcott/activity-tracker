package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.Serializable

@Serializable
enum class FollowRequestsTab {
    /** Requests the athlete hasn't answered. */
    Requests,

    /** Requests the athlete declined, which they can still approve. */
    Declined,
}

/** The signed-in athlete's follow requests, when they approve followers. */
@CircuitSerializable(SessionScope::class) data object FollowRequestsScreen : Screen

@Immutable
data class FollowRequestsState(
    val tab: FollowRequestsTab = FollowRequestsTab.Requests,
    /** The requests, loaded (ui/content-loading), with the athlete's answers here laid over them. */
    val content: ContentState<FollowRequestsContent> = ContentState(FollowRequestsContent()),
    /** The session can't read or write declines, so everyone shows as a request; declining needs the athlete to sign in again. */
    val needsSignIn: Boolean = false,
    /** Accounts whose approval or decline is being written. */
    val answering: ImmutableSet<Did> = persistentSetOf(),
    /** Accounts that can't be approved or declined: their DID isn't a valid record key. */
    val unanswerable: ImmutableSet<Did> = persistentSetOf(),
    /** Why answering a request failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (FollowRequestsEvent) -> Unit = {},
) : CircuitUiState {
    val requests: ImmutableList<ProfileViewBasic>
        get() = content.data.requests

    val declined: ImmutableList<ProfileViewBasic>
        get() = content.data.declined

    /** The accounts the chosen [tab] lists. */
    val accounts: ImmutableList<ProfileViewBasic>
        get() = if (tab == FollowRequestsTab.Requests) requests else declined

    /** Whether the listed accounts can be declined: requests can, and declined accounts already are. */
    val offersDecline: Boolean
        get() = tab == FollowRequestsTab.Requests

    /** Whether the note that only the athlete sees who they declined shows: over a Declined list that lists someone. */
    val showsDeclinedNote: Boolean
        get() = tab == FollowRequestsTab.Declined && declined.isNotEmpty()

    /** Whether the note that only the first requests were read shows: on Requests, when there were more. */
    val showsTruncatedNote: Boolean
        get() = tab == FollowRequestsTab.Requests && content.data.truncated

    /** Why answering failed, or that the session must sign in again to decline, over the accounts; null for neither. */
    val notice: SignInAgainNotice?
        get() = SignInAgainNotice.of(error, needsSignIn)

    /** Whether [account] can be approved or declined now: not while its answer is written, nor when it can't be answered at all. */
    fun canAnswer(account: ProfileViewBasic): Boolean = account.did !in answering && account.did !in unanswerable
}

/** What the follow requests page says, chosen here for the shared and the native UI (dev/shared-display-text). */
object FollowRequestsTexts {
    /** The tab's label. */
    fun tab(tab: FollowRequestsTab): AppText =
        AppText.named(
            when (tab) {
                FollowRequestsTab.Requests -> "follow_requests_tab_requests"
                FollowRequestsTab.Declined -> "follow_requests_tab_declined"
            },
        )

    /** What the tab says when it lists no one. */
    fun empty(tab: FollowRequestsTab): AppText =
        AppText.named(
            when (tab) {
                FollowRequestsTab.Requests -> "follow_requests_none"
                FollowRequestsTab.Declined -> "follow_requests_none_declined"
            },
        )
}

@Immutable
data class FollowRequestsContent(
    val requests: ImmutableList<ProfileViewBasic> = persistentListOf(),
    val declined: ImmutableList<ProfileViewBasic> = persistentListOf(),
    /** There were more requests than were read. */
    val truncated: Boolean = false,
)

sealed interface FollowRequestsEvent {
    data object Back : FollowRequestsEvent

    data class SelectTab(val tab: FollowRequestsTab) : FollowRequestsEvent

    /** Approves [did]'s request, from either tab. */
    data class Approve(val did: Did) : FollowRequestsEvent

    /** Declines [did]'s request, privately. */
    data class Decline(val did: Did) : FollowRequestsEvent

    data class Open(val did: Did) : FollowRequestsEvent

    /** Reloads after an error. */
    data object Retry : FollowRequestsEvent

    /** Signs out, so the athlete can sign in again with access to the declines in their space ([FollowRequestsState.needsSignIn]). */
    data object SignInAgain : FollowRequestsEvent
}
