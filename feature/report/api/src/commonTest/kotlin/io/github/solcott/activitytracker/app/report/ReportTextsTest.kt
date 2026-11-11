package io.github.solcott.activitytracker.app.report

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The report sheet's words and conditions (dev/shared-display-text, "The report sheet reads the same in both UIs"). */
class ReportTextsTest {
    private fun state(reason: ReportReason?, note: String = "") =
        ReportState("Bob", reason, textFieldStateOf(note), sending = false, sent = false, offerBlock = true, error = null) {}

    @Test
    fun theReasonsAreEveryReasonInOrderEachWithItsLabel() {
        assertEquals(ReportReason.entries, ReportTexts.reasons.map { it.reason })
        assertEquals(AppText.named("report_reason_harassment"), ReportReasonChoice(ReportReason.HARASSMENT).label)
        assertEquals(AppText.named("report_reason_other"), ReportTexts.reason(ReportReason.OTHER))
    }

    @Test
    fun onlyOtherRequiresTheNote() {
        assertEquals(AppText.named("report_note"), ReportTexts.note(state(null)))
        assertEquals(AppText.named("report_note"), ReportTexts.note(state(ReportReason.SPAM)))
        val other = state(ReportReason.OTHER)
        assertTrue(other.noteRequired)
        assertEquals(AppText.named("report_note_required"), ReportTexts.note(other))
        assertFalse(other.canSend)
        assertTrue(state(ReportReason.OTHER, note = "Stolen route").canSend)
    }

    @Test
    fun theChosenReasonIsTheStatesOwn() {
        val spam = state(ReportReason.SPAM)
        assertTrue(spam.chose(ReportReasonChoice(ReportReason.SPAM)))
        assertFalse(spam.chose(ReportReasonChoice(ReportReason.OTHER)))
        assertFalse(state(null).chose(ReportReasonChoice(ReportReason.SPAM)))
    }

    @Test
    fun theAuthorIsNamedInTheTitleTheOfferAndTheConfirmation() {
        assertEquals(AppText.named("report_title", AppText.written("Bob")), ReportTexts.title("Bob"))
        assertEquals(AppText.named("report_block_offer", AppText.written("Bob")), ReportTexts.blockOffer("Bob"))
        assertEquals(
            AppText.named("block_confirm_title", AppText.written("Bob")),
            ReportTexts.blockConfirmTitle(BlockOffer(Did("did:plc:bob"), "Bob")),
        )
        assertEquals(1900, ReportTexts.noteLimit)
    }
}
