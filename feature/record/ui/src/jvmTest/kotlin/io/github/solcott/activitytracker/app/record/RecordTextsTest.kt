package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabeledText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the recording screen says and offers, for the shared and the native screen (record/gps-recording, dev/shared-display-text). */
class RecordTextsTest {
    private val samples = RecordSamples.all + RecordSamples.variants

    @Test
    fun eachStatusOffersItsControlsInOrder() {
        assertEquals(listOf(RecordAction.Start), RecordTexts.actions(sample(SampleKind.LOADED)))
        assertEquals(listOf(RecordAction.Pause), RecordTexts.actions(sample("recording")))
        assertEquals(
            listOf(RecordAction.Resume, RecordAction.Finish, RecordAction.Discard),
            RecordTexts.actions(sample("paused")),
        )
    }

    @Test
    fun theTypeChangesOnlyBeforeRecordingStarts() {
        assertTrue(RecordTexts.typeChangeable(sample(SampleKind.LOADED)))
        assertFalse(RecordTexts.typeChangeable(sample("recording")))
        assertFalse(RecordTexts.typeChangeable(sample("paused")))
    }

    @Test
    fun aRideReadsItsTimeDistanceSpeedAndClimb() {
        val ride = sample("recording")
        assertEquals(AppText.written("42:00"), RecordTexts.elapsed(ride))
        assertEquals(
            listOf(
                LabeledText(AppText.named("stat_distance"), StatText.distance(15_300.0, UnitSystem.Metric)),
                checkNotNull(StatText.rate("ride", 15_300.0, 2_400, UnitSystem.Metric)),
                LabeledText(AppText.named("stat_elevation"), StatText.elevation(180.0, UnitSystem.Metric)),
            ),
            RecordTexts.stats(ride, UnitSystem.Metric),
        )
    }

    @Test
    fun aRunInMilesReadsItsPacePerMileAndClimbInFeet() {
        val stats = RecordTexts.stats(sample("run"), UnitSystem.Imperial)
        assertEquals(AppText.named("stat_pace"), stats[1].label)
        assertEquals(StatText.pace(15_300.0, 2_400, UnitSystem.Imperial), stats[1].value)
        assertEquals(StatText.elevation(180.0, UnitSystem.Imperial), stats[2].value)
    }

    @Test
    fun everyNameTheScreenChoosesHasItsString() {
        val texts =
            listOf(RecordTexts.title, RecordTexts.recovered, RecordTexts.saveFailed, RecordTexts.locationOff) +
                listOf(RecordTexts.openSettings, RecordTexts.discardConfirm) +
                RecordAction.entries.map(RecordTexts::label) +
                samples.values.flatMap { state -> UnitSystem.entries.flatMap { RecordTexts.stats(state, it) } }.map { it.label }
        val missing = texts.filterIsInstance<AppText.Named>().map { it.name }.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun sample(name: String) = checkNotNull(samples[name]) { "no sample $name" }
}
