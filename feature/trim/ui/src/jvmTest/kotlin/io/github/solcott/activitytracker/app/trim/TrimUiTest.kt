package io.github.solcott.activitytracker.app.trim

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The trim screen (feed/activity-trim) from its samples. */
@OptIn(ExperimentalTestApi::class)
class TrimUiTest {
    @Test
    fun untrimmedRideOffersTheSuggestionAndNoSave() = runComposeUiTest {
        setContent { AppPreview { TrimUi(TrimSamples.loaded) } }
        onNodeWithText("Started 6 min early and drove 3.26 km after you stopped").assertExists()
        onNodeWithText("Trim").assertIsNotEnabled()
        onNodeWithContentDescription("Trim start").assertExists()
        onNodeWithContentDescription("Trim end").assertExists()
    }

    @Test
    fun trimmedRideShowsWhatItCutsAndTheEffortItRemoves() = runComposeUiTest {
        val events = mutableListOf<TrimEvent>()
        setContent { AppPreview { TrimUi(TrimSamples.trimmed.copy(eventSink = { events += it })) } }
        onNodeWithText("Cuts 6:00", substring = true).assertExists()
        onNodeWithText("was 33.50 km").assertExists()
        onNodeWithText("Your effort on Ridgeline Climb will be removed.").assertExists()
        onNodeWithText("Undo").assertExists()
        onNodeWithText("Trim").assertIsEnabled().performClick()
        assertEquals(listOf<TrimEvent>(TrimEvent.Save), events)
    }

    @Test
    fun manualEntryShowsDistanceDisabledWithItsReason() = runComposeUiTest {
        setContent { AppPreview { TrimUi(TrimSamples.manual) } }
        onNodeWithText("Distance needs a recorded distance or GPS, which this activity doesn't have.").assertExists()
        onAllNodes(hasClickAction() and hasText("Distance")).onFirst().assertIsNotEnabled()
    }

    @Test
    fun confirmationSaysWhatIsCutForGood() = runComposeUiTest {
        setContent { AppPreview { TrimUi(TrimSamples.confirming) } }
        onNodeWithText("6:00 will be cut from the start and 10:00 from the end. The cut data can't be brought back.").assertExists()
    }

    @Test
    fun anUnsentActivitySaysSo() = runComposeUiTest {
        setContent { AppPreview { TrimUi(TrimSamples.notSent) } }
        onNodeWithText("This activity can be trimmed once it's sent.").assertExists()
    }

    @Test
    fun everyNameTheTextsChooseHasItsString() {
        val texts =
            TrimSamples.all.values
                .mapNotNull { it.editor }
                .flatMap { editor ->
                    UnitSystem.entries.flatMap { units ->
                        listOf(true, false).flatMap { start ->
                            listOf(
                                TrimTexts.position(editor, start, units),
                                TrimTexts.handleValue(editor, start, units),
                                TrimTexts.cut(editor, start, units),
                            )
                        } +
                            TrimTexts.totals(editor, units).flatMap { listOfNotNull(it.label, it.value, it.was) } +
                            listOfNotNull(editor.suggestion?.let { TrimTexts.suggestion(it, units) }, TrimTexts.exactLabel(editor, units)) +
                            listOfNotNull(
                                TrimTexts.offer(editor.suggestion, applied = true, units),
                                TrimTexts.profile(editor, units)?.description,
                            )
                    } +
                        TrimTexts.effortNotes(editor) +
                        listOf(
                            TrimTexts.confirm(editor.copy(from = 60)),
                            TrimTexts.confirm(editor.copy(to = editor.elapsed - 60, from = 0)),
                        )
                }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
