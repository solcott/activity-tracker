package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.activityfile.DeviceThresholds
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
}
