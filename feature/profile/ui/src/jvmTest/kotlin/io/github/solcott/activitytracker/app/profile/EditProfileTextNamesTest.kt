package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string edit profile's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class EditProfileTextNamesTest {
    @Test
    fun everyNameTheEditProfileTextsChooseHasItsString() {
        val texts =
            listOf(
                EditProfileTexts.counter("Alice", EditProfileLimits.displayName),
                EditProfileTexts.inchesBeside,
                EditProfileTexts.inchesRow,
            ) +
                UnitSystem.entries.flatMap { listOf(EditProfileTexts.weightLabel(it), EditProfileTexts.heightLabel(it)) } +
                DetailGroup.profile.map(EditProfileTexts::nowHidden) +
                GenderChoices.all.map(GenderChoices::label)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
