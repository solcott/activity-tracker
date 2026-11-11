package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the place-name sheet's texts choose exists (dev/shared-display-text). */
class ProposePlaceNameTextNamesTest {
    @Test
    fun everyNameTheNameSheetTextsChooseHasItsString() {
        val states =
            listOf(false, true).map { ProposePlaceNameState(textFieldStateOf(), it, "Trail 214", sending = false, error = null) {} }
        val limited = ShareLimit.entries.map { states.first().copy(shareLimit = it) }
        val texts =
            states.flatMap { listOf(ProposePlaceNameTexts.title(it), ProposePlaceNameTexts.confirm(it), ProposePlaceNameTexts.lead(it)) } +
                limited.mapNotNull(ProposePlaceNameTexts::shareLimit) +
                listOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS).map(ProposePlaceNameTexts::audience) +
                listOf(
                    ProposePlaceNameTexts.field,
                    ProposePlaceNameTexts.invalid,
                    ProposePlaceNameTexts.shareToFeed,
                    ProposePlaceNameTexts.shareToFeedDetail,
                    ProposePlaceNameTexts.audience,
                    ProposePlaceNameTexts.shareText,
                    ProposePlaceNameTexts.shareTextTooLong,
                    ProposePlaceNameTexts.deleteConfirmTitle,
                    ProposePlaceNameTexts.deleteConfirmMessage,
                    ProposePlaceNameTexts.delete,
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
