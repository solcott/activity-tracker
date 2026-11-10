package io.github.solcott.activitytracker.app.report

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.textfield.textFieldStateOf

object ReportSamples {
    private fun state(reason: ReportReason?, sent: Boolean = false) =
        ReportState(
            authorName = "Bob",
            reason = reason,
            note = textFieldStateOf(),
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
}
