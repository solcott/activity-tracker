package io.github.solcott.activitytracker.app.report

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One of the report sheet's reasons, as an object Swift reads one at a time (a collection of enums can't reach it). */
@Immutable
data class ReportReasonChoice(val reason: ReportReason) {
    val label: AppText
        get() = ReportTexts.reason(reason)
}

/**
 * What the report sheet (`moderation/reports`) and its host's Block confirmation say, chosen once for the shared and the native sheets
 * (dev/shared-display-text).
 */
object ReportTexts {
    /** "Report Bob". */
    fun title(authorName: String): AppText = AppText.named("report_title", AppText.written(authorName))

    /** The menu entry that opens the sheet. */
    val menuEntry: AppText
        get() = AppText.named("action_report")

    val reasonPrompt: AppText
        get() = AppText.named("report_reason_prompt")

    /** Every reason, in the order the sheet lists them. */
    val reasons: ImmutableList<ReportReasonChoice>
        get() = ReportReason.entries.map(::ReportReasonChoice).toImmutableList()

    fun reason(reason: ReportReason): AppText =
        AppText.named(
            when (reason) {
                ReportReason.SPAM -> "report_reason_spam"
                ReportReason.SEXUAL -> "report_reason_sexual"
                ReportReason.VIOLENCE -> "report_reason_violence"
                ReportReason.HARASSMENT -> "report_reason_harassment"
                ReportReason.MISLEADING -> "report_reason_misleading"
                ReportReason.OTHER -> "report_reason_other"
            },
        )

    /** The note's label: optional, or for a reason that needs one, asking what's wrong. */
    fun note(state: ReportState): AppText = AppText.named(if (state.noteRequired) "report_note_required" else "report_note")

    /** The most characters the note holds: a longer edit is refused. */
    val noteLimit: Int
        get() = ReportRepository.MAX_NOTE_LENGTH

    /** What the app's moderators may see of a report, under the note. */
    val privateNote: AppText
        get() = AppText.named("report_private_note")

    val send: AppText
        get() = AppText.named("report_send")

    val thanksTitle: AppText
        get() = AppText.named("report_thanks_title")

    val thanksMessage: AppText
        get() = AppText.named("report_thanks_message")

    /** "Block Bob", offered once the report is sent. */
    fun blockOffer(authorName: String): AppText = AppText.named("report_block_offer", AppText.written(authorName))

    /** The host's Block confirmation for [offer], as the profile's Block asks it. */
    fun blockConfirmTitle(offer: BlockOffer): AppText = AppText.named("block_confirm_title", AppText.written(offer.name))

    val blockConfirmMessage: AppText
        get() = AppText.named("block_confirm_message")

    val block: AppText
        get() = AppText.named("action_block")
}
