package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.data.training.TrainingHistory.Key
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TrainingHistoryTest {
    private val day1 = Instant.parse("2026-09-01T07:00:00Z")
    private val day2 = Instant.parse("2026-09-15T07:00:00Z")
    private val day3 = Instant.parse("2026-10-01T07:00:00Z")
    private val ftp = Threshold.FUNCTIONAL_THRESHOLD_POWER

    @Test
    fun recordAddsAnEntryForEachChangedValue() {
        val before = TrainingDetails(functionalThresholdPower = 250, maxHeartRate = 190)
        val after =
            before.copy(
                functionalThresholdPower = 260,
                maxHeartRate = null,
                setAt = TrainingSetAt(functionalThresholdPower = day2),
                sports = listOf(SportTraining(Sport.RUN, thresholdSpeed = 3271)),
            )

        val recorded = TrainingHistory.record(before, after, ThresholdSource.MANUAL, now = day3)

        // FTP takes its set time; the new run pace has none, so takes now. Clearing max heart rate adds nothing.
        assertEquals(
            listOf(
                ThresholdEntry(ftp, 260, day2, source = ThresholdSource.MANUAL),
                ThresholdEntry(Threshold.THRESHOLD_SPEED, 3271, day3, Sport.RUN, ThresholdSource.MANUAL),
            ),
            recorded.history,
        )
        assertEquals(after, TrainingHistory.record(after, after, ThresholdSource.MANUAL, now = day3))
    }

    @Test
    fun recordKeepsTheNewestEntriesWithinTheLimit() {
        val old = List(TrainingDetails.MAX_HISTORY) { ThresholdEntry(ftp, 200L + it % 50, day1) }
        val before = TrainingDetails(functionalThresholdPower = 249, history = old)

        val recorded =
            TrainingHistory.record(
                before,
                before.copy(functionalThresholdPower = 300),
                ThresholdSource.DEVICE,
                now = day2,
                device = "Edge 840",
            )

        assertEquals(TrainingDetails.MAX_HISTORY, recorded.history.size)
        assertEquals(ThresholdEntry(ftp, 300, day2, source = ThresholdSource.DEVICE, device = "Edge 840"), recorded.history.last())
    }

    @Test
    fun backfillAddsCurrentValuesAsOfWhenTheyWereSet() {
        val training =
            TrainingDetails(
                functionalThresholdPower = 250,
                maxHeartRate = 190,
                setAt = TrainingSetAt(functionalThresholdPower = day1),
                sports =
                    listOf(
                        SportTraining(Sport.RIDE, functionalThresholdPower = 270, setAt = TrainingSetAt(functionalThresholdPower = day2))
                    ),
            )

        // Max heart rate has no set time, so its entry is undated, and oldest.
        val maxHeartRate = ThresholdEntry(Threshold.MAX_HEART_RATE, 190)
        assertEquals(
            listOf(maxHeartRate, ThresholdEntry(ftp, 250, day1), ThresholdEntry(ftp, 270, day2, Sport.RIDE)),
            TrainingHistory.backfilled(training).history,
        )
        val withHistory = training.copy(history = listOf(ThresholdEntry(ftp, 240, day1)))
        assertEquals(
            listOf(maxHeartRate, ThresholdEntry(ftp, 240, day1), ThresholdEntry(ftp, 270, day2, Sport.RIDE)),
            TrainingHistory.backfilled(withHistory).history,
        )
    }

    @Test
    fun removingTheCurrentValueGoesBackToThePreviousOne() {
        val first = ThresholdEntry(ftp, 250, day1, source = ThresholdSource.MANUAL)
        val second = ThresholdEntry(ftp, 260, day2, source = ThresholdSource.TRAINER_ROAD)
        val training =
            TrainingDetails(
                functionalThresholdPower = 260,
                setAt = TrainingSetAt(functionalThresholdPower = day2),
                history = listOf(first, second),
            )

        val reverted = TrainingHistory.remove(training, second)
        assertEquals(250, reverted.functionalThresholdPower)
        assertEquals(day1, reverted.setAt?.functionalThresholdPower)
        assertEquals(listOf(first), reverted.history)

        val cleared = TrainingHistory.remove(reverted, first)
        assertNull(cleared.functionalThresholdPower)
        assertNull(cleared.setAt)
        assertEquals(emptyList(), cleared.history)
    }

    @Test
    fun removingAnOlderValueKeepsTheCurrentOne() {
        val first = ThresholdEntry(ftp, 250, day1)
        val second = ThresholdEntry(ftp, 260, day2)
        val training = TrainingDetails(functionalThresholdPower = 260, history = listOf(first, second))

        assertEquals(training.copy(history = listOf(second)), TrainingHistory.remove(training, first))
        // A value the athlete changed without history (another client, say) stays when the newest entry goes.
        val changed = training.copy(functionalThresholdPower = 275)
        assertEquals(changed.copy(history = listOf(first)), TrainingHistory.remove(changed, second))
    }

    @Test
    fun removingASportsCurrentPaceGoesBackToItsPreviousPace() {
        val first = ThresholdEntry(Threshold.THRESHOLD_SPEED, 3200, day1, Sport.RUN)
        val second = ThresholdEntry(Threshold.THRESHOLD_SPEED, 3300, day3, Sport.RUN)
        val run = SportTraining(Sport.RUN, thresholdSpeed = 3300, setAt = TrainingSetAt(thresholdSpeed = day3))
        val training = TrainingDetails(sports = listOf(run), history = listOf(second, first))

        val reverted = TrainingHistory.remove(training, second)

        assertEquals(
            SportTraining(Sport.RUN, thresholdSpeed = 3200, setAt = TrainingSetAt(thresholdSpeed = day1)),
            reverted.sports.single(),
        )
        assertEquals(listOf(first), TrainingHistory.entries(reverted.history, Key(Threshold.THRESHOLD_SPEED, Sport.RUN)))
    }
}
