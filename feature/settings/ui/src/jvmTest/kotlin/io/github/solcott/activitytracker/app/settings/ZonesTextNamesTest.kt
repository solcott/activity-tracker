package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneBasis
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every string the zones pages' text functions can choose exists (dev/shared-display-text): the names aren't typed resources, so the
 * compiler can't say when one is missing. Zone models are an open set, so only the app's own are held to a string.
 */
class ZonesTextNamesTest {
    @Test
    fun everyZoneTextNamesAString() {
        val zones =
            listOf(Zone("1", ZoneName.Recovery, 0, 100), Zone("2", ZoneName.Aerobic, 101, 200), Zone("3", ZoneName.Tempo, 201, null))
        val texts =
            ZoneKind.entries.flatMap { kind -> PaceUnit.entries.flatMap { unit -> zones.map { ZoneTexts.range(it, kind, unit) } } } +
                ZoneKind.entries.map(ZoneTexts::heading) +
                ZoneBasis.entries.flatMap { listOf(ZoneTexts.basis(it), ZoneTexts.missing(it)) } +
                zones.map(ZoneTexts::label) +
                listOf(true, false).map(PrivacyZonesTexts::addHere) +
                PrivacyZonesTexts.distance(0, io.github.solcott.activitytracker.data.units.UnitSystem.Metric) +
                listOf(SportZonesTexts.title(Sport.RUN), SportZonesTexts.help(Sport.RUN), SportZonesTexts.remove(Sport.RUN)) +
                PaceUnit.entries.map(SportZonesTexts::paceLabel)
        val models = (ZoneModels.heartRate + ZoneModels.power + ZoneModels.speed).map { (ZoneTexts.model(it) as AppText.Named).name }
        val missing = (texts.flatMap(::names) + models).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyThresholdLineNamesAString() {
        val samples = TrainingZonesSamples.all.values + TrainingZonesSamples.variants.values
        val texts =
            samples
                .mapNotNull { it.content.data }
                .flatMap { zones ->
                    (listOf(zones.defaults) + zones.sports).flatMap { summary ->
                        TrainingZonesTexts.thresholds(summary).flatMap { listOf(it.label, it.value) } +
                            TrainingZonesTexts.heading(summary) +
                            TrainingZonesTexts.help(summary)
                    }
                }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Written,
            is AppText.Time -> emptyList()
            is AppText.Named ->
                listOfNotNull(text.name.takeUnless { text.fallback != null && it.startsWith("sport_") }) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
        }
}
