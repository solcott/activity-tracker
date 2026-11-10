package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toPersistentSet

/** What the blocked and muted lists say and when an account's change is in flight (ui/settings-layout, dev/shared-display-text). */
class BlockListTextsTest {
    private fun account(name: String, removable: Boolean = true, onBluesky: Boolean = false) =
        BlockListAccount(ProfileViewBasic(Did("did:plc:$name"), Handle("$name.test"), null, null), removable, onBluesky)

    private fun state(kind: BlockListKind, removing: Set<Did> = emptySet()) =
        BlockListState(kind, pagedStateOf(), removing = removing.toPersistentSet(), eventSink = {})

    @Test
    fun eachListHasItsWords() {
        assertEquals(AppText.named("settings_blocked_accounts"), BlockListTexts.title(BlockListKind.Blocked))
        assertEquals(AppText.named("settings_muted_accounts"), BlockListTexts.title(BlockListKind.Muted))
        assertEquals(AppText.named("blocked_accounts_empty"), BlockListTexts.empty(BlockListKind.Blocked))
        assertEquals(AppText.named("muted_accounts_empty"), BlockListTexts.empty(BlockListKind.Muted))
        assertEquals(AppText.named("action_unblock"), BlockListTexts.remove(BlockListKind.Blocked))
        assertEquals(AppText.named("action_unmute"), BlockListTexts.remove(BlockListKind.Muted))
        assertEquals(UiError.Kind.LoadBlocks, state(BlockListKind.Blocked).loadErrorKind)
        assertEquals(UiError.Kind.LoadMutes, state(BlockListKind.Muted).loadErrorKind)
    }

    @Test
    fun onlyABlockMadeOnBlueskyHasTheNote() {
        assertEquals(AppText.named("blocked_on_bluesky"), BlockListTexts.note(account("dave", removable = false, onBluesky = true)))
        assertNull(BlockListTexts.note(account("bob")))
        assertNull(BlockListTexts.note(account("bob", onBluesky = true)))
    }

    @Test
    fun anAccountIsRemovingWhileItsChangeIsWritten() {
        val removing = state(BlockListKind.Blocked, setOf(Did("did:plc:bob")))
        assertTrue(removing.isRemoving(account("bob")))
        assertFalse(removing.isRemoving(account("carol")))
    }
}
