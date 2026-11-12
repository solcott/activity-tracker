package io.github.solcott.activitytracker.app.edit

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the edit form's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class EditTextNamesTest {
    @Test
    fun everyNameTheFormChoosesHasItsString() {
        val texts = Visibility.all.map(EditActivityTexts::coreChartsBody)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
