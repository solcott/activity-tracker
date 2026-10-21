package io.github.solcott.activitytracker.app.report

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did

/**
 * The report sheet (`moderation/reports`) for [target], by [authorName]. Once sent it thanks the athlete and, when [offerBlock], offers to
 * block the author ([ReportOuterEvent.Block]), which the host confirms as its own Block does.
 */
@Immutable data class ReportScreen(val target: ReportTarget, val authorName: String, val offerBlock: Boolean) : SubScreen<ReportOuterEvent>

@Immutable
sealed interface ReportOuterEvent : SubCircuitOuterEvent {
    /** The sheet is done, sent or not. */
    data object Closed : ReportOuterEvent

    /** The athlete took the offer to block [author]; the sheet is done. */
    data class Block(val author: Did) : ReportOuterEvent
}

@Immutable
data class ReportState(
    val authorName: String,
    /** Null until the athlete picks one. */
    val reason: ReportReason?,
    /** Optional unless [reason] is [ReportReason.OTHER], at most `ReportRepository.MAX_NOTE_LENGTH`. */
    val note: TextFieldState,
    val sending: Boolean,
    /** The report went: the sheet thanks the athlete. */
    val sent: Boolean,
    /** Once [sent], Block is offered: the author isn't blocked already. */
    val offerBlock: Boolean,
    /** Why the last send failed; the reason and note stay for a retry. */
    val error: UiError?,
    val eventSink: (ReportEvent) -> Unit,
) : SubCircuitUiState {
    val canSend: Boolean
        get() = reason != null && !sending && (!reason.requiresNote || note.text.isNotBlank())
}

sealed interface ReportEvent {
    data class PickReason(val reason: ReportReason) : ReportEvent

    data object Send : ReportEvent

    /** Takes the offer to block the author. */
    data object Block : ReportEvent

    data object Close : ReportEvent
}

/**
 * Report as a host carries it, a sub-state with its own sink: the open sheet, and the block it offered once the athlete took it, waiting
 * for the host's usual Block confirmation.
 */
@Immutable
data class Reporting(
    /** Report is offered at all: a labeler is configured. Each host still leaves it off the viewer's own content. */
    val available: Boolean = false,
    val sheet: ReportScreen? = null,
    /** The author to block, named, while the confirmation shows. */
    val confirmBlock: BlockOffer? = null,
    val eventSink: (ReportingEvent) -> Unit = {},
)

@Immutable data class BlockOffer(val author: Did, val name: String)

sealed interface ReportingEvent {
    /** Opens the sheet for [target]; [offerBlock] when the viewer doesn't block its author already. */
    data class Open(val target: ReportTarget, val authorName: String, val offerBlock: Boolean = true) : ReportingEvent

    data class Sheet(val event: ReportOuterEvent) : ReportingEvent

    /** The block confirmation's answer. */
    data class ConfirmBlock(val confirmed: Boolean) : ReportingEvent
}
