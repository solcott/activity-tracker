package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

/** Every string the filter bar's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class FeedFilterTextNamesTest {
    @Test
    fun everyNameTheFilterTextsChooseHasItsString() {
        val types =
            listOf(
                persistentSetOf(),
                persistentSetOf(ActivityType(Sport.RIDE)),
                persistentSetOf(ActivityType(Sport.RIDE), ActivityType(Sport.RUN)),
            )
        val texts =
            types.map { FeedFilterTexts.types(FeedFilter(it)) } +
                (FeedFilterTexts.rangeChoices().map { it.range } + DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 2))).map(
                    FeedFilterTexts::range,
                )
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
