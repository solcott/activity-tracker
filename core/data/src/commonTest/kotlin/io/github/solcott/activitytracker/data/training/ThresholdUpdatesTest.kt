package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ThresholdUpdatesTest {
    private val setOn = Instant.parse("2026-09-01T00:00:00Z")
    private val later = Instant.parse("2026-10-02T07:00:00Z")
    private val earlier = Instant.parse("2026-08-01T07:00:00Z")
    private val device =
        DeviceThresholds(maxHeartRate = 188, thresholdHeartRate = 170, functionalThresholdPower = 240, restingHeartRate = 48)
    private val current =
        TrainingDetails(
            functionalThresholdPower = 231,
            maxHeartRate = 190,
            thresholdHeartRate = 170,
            autoUpdate = listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER, Threshold.THRESHOLD_HEART_RATE, Threshold.RESTING_HEART_RATE),
            setAt = TrainingSetAt(functionalThresholdPower = setOn, thresholdHeartRate = setOn),
        )

    @Test
    fun onlyChosenThresholdsThatDifferUpdate() {
        // Max HR isn't chosen, and LTHR is already 170.
        assertEquals(
            listOf(
                ThresholdChange(Threshold.FUNCTIONAL_THRESHOLD_POWER, 231, 240),
                ThresholdChange(Threshold.RESTING_HEART_RATE, null, 48),
            ),
            ThresholdUpdates.pending(current, device, later),
        )
    }

    @Test
    fun nothingUpdatesUntilChosen() {
        assertEquals(emptyList(), ThresholdUpdates.pending(current.copy(autoUpdate = emptyList()), device, later))
        assertEquals(emptyList(), ThresholdUpdates.pending(null, device, later))
        assertEquals(emptyList(), ThresholdUpdates.pending(current, null, later))
    }

    @Test
    fun anOlderActivityOnlyFillsThresholdsNeverSet() {
        assertEquals(listOf(ThresholdChange(Threshold.RESTING_HEART_RATE, null, 48)), ThresholdUpdates.pending(current, device, earlier))
    }

    @Test
    fun applyingMarksEachChangeAsSetAtTheActivityStart() {
        val updated = ThresholdUpdates.apply(current, ThresholdUpdates.pending(current, device, later), later)
        assertEquals(240, updated.functionalThresholdPower)
        assertEquals(48, updated.restingHeartRate)
        assertEquals(190, updated.maxHeartRate)
        assertEquals(TrainingSetAt(functionalThresholdPower = later, thresholdHeartRate = setOn, restingHeartRate = later), updated.setAt)
        // Applied once, the same activity brings nothing more.
        assertEquals(emptyList(), ThresholdUpdates.pending(updated, device, later))
    }

    @Test
    fun aSportsOverrideTakesItsDevicesThresholds() {
        val run = SportTraining(sport = Sport.RUN, thresholdHeartRate = 175, setAt = TrainingSetAt(thresholdHeartRate = setOn))
        val withRun = current.copy(sports = listOf(run))
        val runDevice = device.copy(thresholdHeartRate = 178, functionalThresholdPower = 300)
        val changes = ThresholdUpdates.pending(withRun, runDevice, later, Sport.RUN)
        // FTP isn't overridden, so it's compared with the default's and goes into the override; resting HR stays a default.
        assertEquals(
            listOf(
                ThresholdChange(Threshold.FUNCTIONAL_THRESHOLD_POWER, 231, 300, Sport.RUN),
                ThresholdChange(Threshold.THRESHOLD_HEART_RATE, 175, 178, Sport.RUN),
                ThresholdChange(Threshold.RESTING_HEART_RATE, null, 48),
            ),
            changes,
        )
        val updated = ThresholdUpdates.apply(withRun, changes, later)
        assertEquals(231, updated.functionalThresholdPower)
        assertEquals(170, updated.thresholdHeartRate)
        assertEquals(48, updated.restingHeartRate)
        assertEquals(
            SportTraining(
                sport = Sport.RUN,
                functionalThresholdPower = 300,
                thresholdHeartRate = 178,
                setAt = TrainingSetAt(functionalThresholdPower = later, thresholdHeartRate = later),
            ),
            updated.sports.single(),
        )
        assertEquals(emptyList(), ThresholdUpdates.pending(updated, runDevice, later, Sport.RUN))
    }

    @Test
    fun aSportWithoutAnOverrideUpdatesTheDefaults() {
        val withRun = current.copy(sports = listOf(SportTraining(sport = Sport.RUN, thresholdHeartRate = 175)))
        assertEquals(
            ThresholdUpdates.pending(current, device, later),
            ThresholdUpdates.pending(withRun, device, later, Sport.RIDE),
        )
    }
}
