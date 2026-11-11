package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/**
 * Every string the gear item page's shared text names exists (dev/shared-display-text), as `SharedTextNamesTest` holds for
 * `:app-common-api`.
 */
class GearPageTextNamesTest {
    @Test
    fun everyPageTextNamesAString() {
        val page = GearSamples.pageWithParts.copy(retired = true)
        val component = page.components.first()
        val past = page.previous.first()
        val texts =
            UnitSystem.entries.flatMap { units ->
                (GearPageTexts.figures(page, units) + GearPageTexts.bySport(page, units) + GearPageTexts.byYear(page, units)).flatMap {
                    listOf(it.label, it.value)
                } + listOfNotNull(GearPageTexts.starting(page.usage.starting, units), GearPageTexts.componentLine(component, units))
            } +
                listOfNotNull(
                    GearPageTexts.kindLine(page.gear),
                    GearPageTexts.notes(page),
                    GearPageTexts.componentDates(component, TimeZone.UTC),
                    GearPageTexts.componentDates(past, TimeZone.UTC),
                    GearPageTexts.mountDates(GearSamples.chainPage.mounts.first(), TimeZone.UTC),
                    GearPageTexts.componentActions(component),
                    GearPageTexts.pickerTitle(GearMountPicker(persistentListOf())),
                    GearPageTexts.pickerTitle(GearMountPicker(persistentListOf(), replacing = component)),
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
