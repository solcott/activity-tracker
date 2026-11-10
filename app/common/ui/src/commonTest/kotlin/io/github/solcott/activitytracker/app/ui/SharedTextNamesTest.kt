package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.FailureReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.icon.SportIconNames
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allDrawableResources
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every string and image name shared code can choose exists (dev/shared-display-text). The choices are names, not typed resources, so the
 * compiler no longer says when one is missing; this does, for every kind of error and reason, every unit formatter in both unit systems,
 * every closed label and every sport's image. Labels from open sets are left out: a missing one is their fallback's case.
 */
class SharedTextNamesTest {
    @Test
    fun everyKindOfErrorHasItsString() {
        val missing = UiError.Kind.entries.map(ErrorTexts::kindName).filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyReasonHasItsString() {
        val reasons = FailureReason.entries.map(ErrorTexts::reasonName) + "error_with_reason"
        val missing = reasons.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyFormatterNamesAString() {
        val texts =
            UnitSystem.entries.flatMap { units ->
                listOf(
                    StatText.missing,
                    StatText.distance(1_000.0, units),
                    StatText.pace(1_000.0, 300, units),
                    StatText.speed(5_000, units),
                    StatText.swimPace(1_000.0, 1_200, units),
                    StatText.elevation(100.0, units),
                    StatText.depth(18.0, units),
                    StatText.temperature(200, units),
                    StatText.weight(70_000, units),
                ) +
                    listOf(Sport.RUN, Sport.RIDE, Sport.SWIM).flatMap { sport ->
                        StatText.rate(sport, 1_000.0, 300, units).let { listOfNotNull(it?.label, it?.value) }
                    }
            }
        assertAllExist(texts)
    }

    @Test
    fun everyClosedLabelNamesAString() {
        val texts =
            DetailGroup.entries.flatMap { listOf(LabelText.detailGroup(it), LabelText.visibilityScope(it)) } +
                listOf(Gender.FEMALE, Gender.MALE, Gender.NON_BINARY).map(LabelText::gender) +
                Visibility.all.map(LabelText::visibility) +
                LabelText.handle("alice.test") +
                // The string an unknown sensor falls back to.
                (LabelText.sensorType("unknown") as AppText.Named).fallback!!
        assertAllExist(texts)
    }

    @Test
    fun everySportImageExists() {
        val missing = SportIconNames.all.filterNot { it in Res.allDrawableResources }
        assertTrue(missing.isEmpty(), "No drawable for $missing")
    }

    private fun assertAllExist(texts: List<AppText>) {
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    /** The names [text] and its arguments choose. */
    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Written -> emptyList()
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
        }
}
