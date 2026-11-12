package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * What the choice after leaving on another device says, which both UIs ask (dev/shared-display-text, "The left-elsewhere choice reads the
 * same in both UIs").
 */
class LeftElsewhereTextsTest {
    private val state = LeftElsewhereState(handle = "bob.test", checking = false, eventSink = {})

    @Test
    fun theIntroNamesTheHandle() {
        assertEquals(AppText.named("left_elsewhere_intro", AppText.written("bob.test")), LeftElsewhereTexts.intro(state))
    }

    @Test
    fun onlyWhatTheDeviceHoldsIsListed() {
        assertEquals(emptyList(), LeftElsewhereTexts.lines(state))
        assertEquals(listOf(AppText.named("left_elsewhere_recording")), LeftElsewhereTexts.lines(state.copy(recording = true)))
    }

    @Test
    fun everythingIsListedInOrderWithTheCountGrouped() {
        assertEquals(
            listOf(
                AppText.Plural("left_elsewhere_changes", 1200, persistentListOf(AppText.written("1,200"))),
                AppText.named("left_elsewhere_import"),
                AppText.named("left_elsewhere_export"),
                AppText.named("left_elsewhere_recording"),
            ),
            LeftElsewhereTexts.lines(state.copy(changes = 1200, importSaving = true, exportPending = true, recording = true)),
        )
    }
}
