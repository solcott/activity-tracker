package io.github.solcott.activitytracker.app.report

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the report sheet's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class ReportTextNamesTest {
    @Test
    fun everyNameTheReportTextsChooseHasItsString() {
        val states =
            listOf(null, ReportReason.OTHER).map {
                ReportState("Bob", it, textFieldStateOf(), sending = false, sent = false, offerBlock = true, error = null) {}
            }
        val texts =
            ReportTexts.reasons.map { it.label } +
                states.map(ReportTexts::note) +
                listOf(
                    ReportTexts.title("Bob"),
                    ReportTexts.menuEntry,
                    ReportTexts.reasonPrompt,
                    ReportTexts.privateNote,
                    ReportTexts.send,
                    ReportTexts.thanksTitle,
                    ReportTexts.thanksMessage,
                    ReportTexts.blockOffer("Bob"),
                    ReportTexts.blockConfirmTitle(BlockOffer(Did("did:plc:bob"), "Bob")),
                    ReportTexts.blockConfirmMessage,
                    ReportTexts.block,
                )
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
