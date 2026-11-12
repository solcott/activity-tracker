package io.github.solcott.activitytracker.app.report

import androidx.compose.runtime.Immutable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.textfield.TextFieldState

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
    /**
     * The note has text, as the presenter read it when it made this state. Reading it there makes typing that fills or empties the note
     * publish a new state, which is how a native sheet's Send follows [canSend]; [canSend] itself reads the note live.
     */
    val noteWritten: Boolean = note.text.isNotBlank(),
    val eventSink: (ReportEvent) -> Unit,
) : SubCircuitUiState {
    val canSend: Boolean
        get() = reason != null && !sending && (!reason.requiresNote || note.text.isNotBlank())

    /** The chosen reason needs a note: the note's label says so, and Send waits for one. */
    val noteRequired: Boolean
        get() = reason?.requiresNote == true

    /** Whether [choice] is the chosen reason. */
    fun chose(choice: ReportReasonChoice): Boolean = reason == choice.reason
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
) {
    /**
     * What a comment's Report sends to [eventSink]: the sheet for [comment] by its URI and CID, naming its author [authorName] as each UI
     * shows them (their display name, else their `@handle`), so neither UI builds a report target (dev/shared-display-text).
     */
    fun reportComment(comment: CommentView, authorName: String): ReportingEvent.Open =
        ReportingEvent.Open(ReportTarget.Comment(comment.uri, comment.cid, comment.author.did), authorName)
}

@Immutable data class BlockOffer(val author: Did, val name: String)

sealed interface ReportingEvent {
    /** Opens the sheet for [target]; [offerBlock] when the viewer doesn't block its author already. */
    data class Open(val target: ReportTarget, val authorName: String, val offerBlock: Boolean = true) : ReportingEvent

    data class Sheet(val event: ReportOuterEvent) : ReportingEvent

    /** The block confirmation's answer. */
    data class ConfirmBlock(val confirmed: Boolean) : ReportingEvent
}
