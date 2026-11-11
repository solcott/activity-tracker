package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The place-name sheet's words and conditions (dev/shared-display-text, "The place-name sheet reads the same in both UIs"). */
class ProposePlaceNameTextsTest {
    private fun state(name: String, editing: Boolean = false, sending: Boolean = false) =
        ProposePlaceNameState(textFieldStateOf(name), editing, "Trail 214", sending = sending, error = null, eventSink = {})

    @Test
    fun aNewProposalAndAnEditSayDifferentThings() {
        val propose = state("Wiggly Woods")
        val edit = state("Rooty Ridge", editing = true)
        assertEquals(AppText.named("segment_name_sheet_propose_title"), ProposePlaceNameTexts.title(propose))
        assertEquals(AppText.named("segment_name_sheet_edit_title"), ProposePlaceNameTexts.title(edit))
        assertEquals(AppText.named("segment_name_sheet_send"), ProposePlaceNameTexts.confirm(propose))
        assertEquals(AppText.named("segment_name_sheet_save"), ProposePlaceNameTexts.confirm(edit))
        assertEquals(AppText.named("segment_name_disclosure"), ProposePlaceNameTexts.lead(propose))
        assertEquals(AppText.named("segment_name_edit_warning"), ProposePlaceNameTexts.lead(edit))
    }

    @Test
    fun theInvalidNoteShowsOnlyForATypedNameThatCantBeSent() {
        assertFalse(state("").showsInvalid, "nothing typed yet")
        assertTrue(state("trail 214").showsInvalid, "the current name, in any case")
        assertTrue(state("x".repeat(ProposePlaceNameTexts.nameLimit + 1)).showsInvalid)
        assertFalse(state("Wiggly Woods").showsInvalid)
    }

    @Test
    fun sharingIsOffWhileSendingOrReadingAnEditsSharing() {
        assertTrue(state("Wiggly Woods").shareEnabled)
        assertFalse(state("Wiggly Woods", sending = true).shareEnabled)
        assertFalse(state("Wiggly Woods").copy(shareLoading = true).shareEnabled)
    }

    @Test
    fun onlyALimitedShareSaysWhy() {
        assertNull(ProposePlaceNameTexts.shareLimit(state("a")))
        assertEquals(
            AppText.named("post_share_public_only_sign_in"),
            ProposePlaceNameTexts.shareLimit(state("a").copy(shareLimit = ShareLimit.SignInAgain)),
        )
        assertEquals(
            AppText.named("post_share_public_only_unsupported"),
            ProposePlaceNameTexts.shareLimit(state("a").copy(shareLimit = ShareLimit.SpacesUnsupported)),
        )
        assertEquals(AppText.named("post_share_text_too_long", AppText.written("1000")), ProposePlaceNameTexts.shareTextTooLong)
    }
}
