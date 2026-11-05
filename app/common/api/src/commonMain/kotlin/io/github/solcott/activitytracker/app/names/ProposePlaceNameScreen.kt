package io.github.solcott.activitytracker.app.names

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.app.UiError

/**
 * The sheet that proposes a name for the place [place] (segments/segment-names), whose current name is [currentName]; or, with [proposal],
 * edits the name of the athlete's own open proposal, starting from [proposalName]. [place] is the AT-URI of the named place, such as a
 * segment.
 */
@Immutable
data class ProposePlaceNameScreen(
    val place: String,
    val currentName: String,
    val proposal: String? = null,
    val proposalName: String? = null,
) : SubScreen<ProposePlaceNameOuterEvent>

@Immutable
sealed interface ProposePlaceNameOuterEvent : SubCircuitOuterEvent {
    /** The sheet is done: the name was queued, or the athlete cancelled. */
    data object Closed : ProposePlaceNameOuterEvent
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

    val canSend: Boolean
        get() = valid && !sending

    companion object {
        /** The lexicon's 100 graphemes, counted as characters here: a longer name is refused before it's sent. */
        const val MAX_NAME_LENGTH = 100
    }
}

sealed interface ProposePlaceNameEvent {
    data object Send : ProposePlaceNameEvent

    data object Cancel : ProposePlaceNameEvent
}
