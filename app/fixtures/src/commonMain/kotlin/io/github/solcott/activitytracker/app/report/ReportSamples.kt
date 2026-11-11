package io.github.solcott.activitytracker.app.report

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError

object ReportSamples {
    private fun state(reason: ReportReason?, sent: Boolean = false, note: String = "") =
        ReportState(
            authorName = "Bob",
            reason = reason,
            note = textFieldStateOf(note),
            sending = false,
            sent = sent,
            offerBlock = true,
            error = null,
            eventSink = {},
        )

    val default: ReportState
        get() = state(null)

    val all: Map<String, ReportState>
        get() = mapOf(SampleKind.LOADED to default, "chosen" to state(ReportReason.SPAM), "sent" to state(ReportReason.SPAM, sent = true))

    /**
     * Beyond [all], which the shared screenshots render: every step the native sheet draws. Other with no note yet, sending, a failure that
     * kept the reason and note, and sent on a profile the viewer blocks already, with no Block offer.
     */
    val variants: Map<String, ReportState>
        get() =
            all +
                mapOf(
                    "other" to state(ReportReason.OTHER),
                    "sending" to state(ReportReason.HARASSMENT, note = "Rude comments on every ride").copy(sending = true),
                    "failed" to
                        state(ReportReason.HARASSMENT, note = "Rude comments on every ride")
                            .copy(error = UiError(UiError.Kind.SendReport, DataError.Network)),
                    "sentNoBlock" to state(ReportReason.SPAM, sent = true).copy(offerBlock = false),
                )
}
