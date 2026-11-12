package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the manual form's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class RecordTextNamesTest {
    @Test
    fun everyNameTheFormChoosesHasItsString() {
        val texts = UnitSystem.entries.flatMap { listOf(ManualActivityTexts.distanceLabel(it), ManualActivityTexts.temperatureLabel(it)) }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
