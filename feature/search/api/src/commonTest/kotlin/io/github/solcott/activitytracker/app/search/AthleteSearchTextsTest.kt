package io.github.solcott.activitytracker.app.search

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the Search tab says, which both UIs ask (dev/shared-display-text, "The Search tab reads the same in both UIs"). */
class AthleteSearchTextsTest {
    private fun state(typed: String) = AthleteSearchState(textFieldStateOf(typed), pagedStateOf<ProfileViewBasic, Unit>(), eventSink = {})

    @Test
    fun theTitleFollowsTheMode() {
        assertEquals(AppText.named("search_title"), AthleteSearchTexts.title(SearchMode.Athletes))
        assertEquals(AppText.named("search_tab_title"), AthleteSearchTexts.title(SearchMode.Segments))
        assertEquals(AppText.named("search_mode_segments"), AthleteSearchTexts.mode(SearchMode.Segments))
    }

    @Test
    fun aBlankQueryPromptsAndIsNeverEmpty() {
        assertEquals(AppText.named("search_prompt"), AthleteSearchTexts.prompt(state("  ")))
        assertNull(AthleteSearchTexts.empty(state("  ")))
    }

    @Test
    fun noMatchNamesTheTrimmedQuery() {
        assertNull(AthleteSearchTexts.prompt(state("  zzz ")))
        assertEquals(AppText.named("search_no_results", AppText.written("zzz")), AthleteSearchTexts.empty(state("  zzz ")))
    }
}
