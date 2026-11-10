package io.github.solcott.activitytracker.app.names

import androidx.compose.runtime.Immutable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.segments.SharedPlace
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The sheet that proposes a name for the place [place] (segments/segment-names), whose current name is [currentName]; or, with [proposal],
 * edits the name of the athlete's own open proposal, starting from [proposalName], as [proposalView] lists it. [place] is the AT-URI of the
 * named place, such as a segment, and [sharedPlace] its brief view, which a post sharing the proposal shows at once (feed/posts).
 */
@Immutable
data class ProposePlaceNameScreen(
    val place: String,
    val currentName: String,
    val proposal: String? = null,
    val proposalName: String? = null,
    val proposalView: SegmentNameProposalView? = null,
    val sharedPlace: SharedPlace? = null,
) : SubScreen<ProposePlaceNameOuterEvent>

@Immutable
sealed interface ProposePlaceNameOuterEvent : SubCircuitOuterEvent {
    /** The sheet is done: the name was queued, or the athlete cancelled. */
    data object Closed : ProposePlaceNameOuterEvent
}

/** Why a proposal can be shared only publicly (feed/posts). */
enum class ShareLimit {
    /** Followers and Mutuals are offered. */
    None,

    /** The session's grant predates posts in spaces: signing in again lifts it. */
    SignInAgain,

    /** The PDS can't hold spaces at all. */
    SpacesUnsupported,
}

@Immutable
data class ProposePlaceNameState(
    /** The proposed name; for an edit, it starts as the proposal's. */
    val name: TextFieldState,
    /** It edits an open proposal: the sheet warns that saving clears its votes, where a new one tells what proposing shows. */
    val editing: Boolean,
    /** The name that may not be proposed again: the place's current one, or for an edit the proposal's own. */
    val unchangedName: String,
    val sending: Boolean,
    /** Why the last send failed; the name stays for a retry. */
    val error: UiError?,
    /** Share to feed is on (feed/posts): a post shares the proposal with [audience], with [shareText]. Off by default. */
    val sharing: Boolean = false,
    /** A [Visibility] among [audiences]. */
    val audience: String = Visibility.PUBLIC,
    /** Why the athlete proposes the name, shown on the post; optional. */
    val shareText: TextFieldState = textFieldStateOf(),
    /** The audiences offered: Public, Followers and Mutuals, or only Public under a [shareLimit]. */
    val audiences: ImmutableList<String> = persistentListOf(Visibility.PUBLIC),
    val shareLimit: ShareLimit = ShareLimit.None,
    /** For an edit, the proposal's sharing is still being read: nothing is sent until it is. */
    val shareLoading: Boolean = false,
    /** How the proposal was shared when the sheet opened, which an edit compares against. */
    val initialSharing: Boolean = false,
    val initialAudience: String = Visibility.PUBLIC,
    val initialShareText: String = "",
    val eventSink: (ProposePlaceNameEvent) -> Unit,
) : SubCircuitUiState {
    /** The name as it would be sent: trimmed, not empty, at most [MAX_NAME_LENGTH] characters, and not [unchangedName]. */
    val valid: Boolean
        get() {
            val trimmed = name.text.trim()
            return trimmed.isNotEmpty() &&
                trimmed.length <= MAX_NAME_LENGTH &&
                !trimmed.toString().equals(unchangedName.trim(), ignoreCase = true)
        }

    /** The share text fits: at most [MAX_SHARE_TEXT_LENGTH] characters. */
    val shareTextValid: Boolean
        get() = shareText.text.trim().length <= MAX_SHARE_TEXT_LENGTH

    /** The athlete changed how the proposal is shared since the sheet opened. */
    val shareChanged: Boolean
        get() =
            sharing != initialSharing || (sharing && (audience != initialAudience || shareText.text.trim().toString() != initialShareText))

    /** The name is the one the edit started from: saving then changes only the sharing. */
    private val nameUnchanged: Boolean
        get() = name.text.trim().toString().equals(unchangedName.trim(), ignoreCase = true)

    val canSend: Boolean
        get() {
            val shareReady = !sharing || shareTextValid
            val busy = sending || shareLoading
            return when {
                busy || !shareReady -> false
                editing -> valid || (nameUnchanged && shareChanged)
                else -> valid
            }
        }

    companion object {
        /** The lexicon's 100 graphemes, counted as characters here: a longer name is refused before it's sent. */
        const val MAX_NAME_LENGTH = 100

        /** The post text's 1000 graphemes, counted as characters here, as for the name. */
        const val MAX_SHARE_TEXT_LENGTH = 1000
    }
}

sealed interface ProposePlaceNameEvent {
    data object Send : ProposePlaceNameEvent

    data object Cancel : ProposePlaceNameEvent

    /** Share to feed turned on or off. */
    data class SharingChanged(val sharing: Boolean) : ProposePlaceNameEvent

    /** The post's audience, a [Visibility] among the state's audiences. */
    data class AudienceChanged(val audience: String) : ProposePlaceNameEvent
}
