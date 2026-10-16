package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityMerge
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.lexicon.app.WaterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ActivityMergeTest {
    @Test
    fun fillsOnlyWhatTheActivityLacks() {
        val existing =
            ActivityRecord(
                Sport.DIVE,
                Instant.parse("2024-05-12T16:00:00Z"),
                2700,
                Instant.parse("2024-05-13T00:00:00Z"),
                title = "Blue Hole",
            )
        val other =
            existing.copy(
                startedAt = Instant.parse("2024-05-12T16:01:00Z"),
                elapsedTime = 2600,
                title = "Morning Dive",
                description = "From the backup computer",
                device = "Suunto D5",
                avgTemperature = 260,
            )
        assertEquals(existing.copy(device = "Suunto D5", avgTemperature = 260), ActivityMerge.fillGaps(existing, other))
        assertEquals(
            existing.copy(device = "Suunto D5", avgTemperature = 260),
            ActivityMerge.fillGaps(existing.copy(avgTemperature = 260), other),
        )
    }

    @Test
    fun fillsTheDiveSummaryFieldByField() {
        val existing = ActivityRecord(Sport.DIVE, Instant.parse("2024-05-12T16:00:00Z"), 2700, Instant.parse("2024-05-13T00:00:00Z"))
        val other = existing.copy(dive = ActivityDive(maxDepth = 3100, avgDepth = 1500, diveNumber = 7))
        assertEquals(other, ActivityMerge.fillGaps(existing, other))
        assertEquals(
            existing.copy(dive = ActivityDive(maxDepth = 3012, avgDepth = 1500, diveNumber = 7)),
            ActivityMerge.fillGaps(existing.copy(dive = ActivityDive(maxDepth = 3012)), other),
        )
    }

    @Test
    fun resamplesAMissingChannelOntoTheExistingTimes() {
        val existing =
            ActivityStreamData(time = listOf(0, 10, 20, 30, 100), depth = listOf(0, 5, 10, 15, 0), temperature = listOf(1, 2, 3, 4, 5))
        // Its clock started 5 s later, so its 0 is the existing 5.
        val other = ActivityStreamData(time = listOf(0, 10, 20), heartRate = listOf(80, 90, 100), depth = listOf(9, 9, 9))
        val merged = ActivityMerge.fillMissing(existing, other, offsetSeconds = 5)
        assertEquals(listOf(80, 80, 90, 100, null), merged.heartRate, "nearest sample, none beyond 30 s")
        assertEquals(existing.depth, merged.depth, "never overwrites a channel")
        assertEquals(existing.temperature, merged.temperature)
    }

    @Test
    fun addsChannelsOnlyWhenTheActivityLacksOne() {
        val depth = ActivityStreamData(time = listOf(0, 10), depth = listOf(0, 500))
        val depthAndTemperature = depth.copy(temperature = listOf(260, 255))
        assertTrue(ActivityMerge.addsChannels(null, depth), "an activity without streams takes them all")
        assertTrue(ActivityMerge.addsChannels(depth, depthAndTemperature))
        assertFalse(ActivityMerge.addsChannels(depthAndTemperature, depth))
        assertFalse(ActivityMerge.addsChannels(depthAndTemperature, depthAndTemperature))
    }

    private val ride = ActivityRecord(Sport.RIDE, Instant.parse("2026-10-02T19:00:00Z"), 3600, Instant.parse("2026-10-02T21:00:00Z"))

    @Test
    fun aBikeComputerAddsPowerAndKeepsTheWatchsHeartRate() {
        val watch = ride.copy(avgHeartRate = 150, calories = 900)
        val bike = ride.copy(avgHeartRate = 140, avgPower = 210, normalizedPower = 232, work = 750, calories = 950, rearShifts = 240)
        val merged = ActivityMerge.fillGaps(watch, bike)
        assertEquals(150, merged.avgHeartRate)
        assertEquals(900, merged.calories, "never overwritten")
        assertEquals(Triple(210, 232, 750L), Triple(merged.avgPower, merged.normalizedPower, merged.work))
        assertEquals(240, merged.rearShifts)

        val watchStreams = ActivityStreamData(time = listOf(0, 10, 20), heartRate = listOf(120, 130, 140))
        val bikeStreams =
            ActivityStreamData(
                time = listOf(0, 10, 20),
                heartRate = listOf(1, 1, 1),
                power = listOf(200, 210, 220),
                leftRightBalance = listOf(51, 52, 50),
            )
        val streams = ActivityMerge.fillMissing(watchStreams, bikeStreams, offsetSeconds = 0)
        assertEquals(watchStreams.heartRate, streams.heartRate)
        assertEquals(listOf(200, 210, 220), streams.power)
        assertEquals(listOf(51, 52, 50), streams.leftRightBalance, "every channel in the table, not just the first ones")
        assertTrue(ActivityMerge.addsChannels(watchStreams, bikeStreams))
    }

    @Test
    fun fillsTheDivesNewerFields() {
        val existing = ride.copy(sport = Sport.DIVE, dive = ActivityDive(maxDepth = 3000, endN2 = 60))
        val other =
            existing.copy(dive = ActivityDive(endN2 = 10, startN2 = 5, waterType = WaterType.SALT, visibility = 1500, highestPpO2 = 1400))
        assertEquals(
            ActivityDive(maxDepth = 3000, endN2 = 60, startN2 = 5, waterType = WaterType.SALT, visibility = 1500, highestPpO2 = 1400),
            ActivityMerge.fillGaps(existing, other).dive,
        )
    }

    @Test
    fun aLogbooksSwitchesAreShiftedOntoTheLoggedDivesStart() {
        val logged = ActivityStreamData(time = listOf(0, 600, 1200, 2400), depth = listOf(0, 3000, 1800, 0))
        // The logbook's dive started a minute after the logged one.
        val logbook =
            ActivityStreamData(
                time = listOf(0, 1500, 2300),
                depth = listOf(0, 600, 0),
                gasSwitches = listOf(GasSwitch(0, 32, 0), GasSwitch(1500, 50, 0)),
            )
        val merged = ActivityMerge.fillMissing(logged, logbook, offsetSeconds = 60)
        assertEquals(listOf(GasSwitch(60, 32, 0), GasSwitch(1560, 50, 0)), merged.gasSwitches)
        assertEquals(logged.depth, merged.depth)

        val own = logged.copy(gasSwitches = listOf(GasSwitch(0, 21, 0)))
        assertEquals(own.gasSwitches, ActivityMerge.fillMissing(own, logbook, offsetSeconds = 60).gasSwitches, "a dive's own switches stay")
        assertTrue(ActivityMerge.addsChannels(logged, logbook))
        assertFalse(ActivityMerge.addsChannels(own, logbook.copy(depth = null)))
    }

    @Test
    fun shiftingOntoALaterStartKeepsTheGasBreathedThenAtZero() {
        val data =
            ActivityStreamData(time = listOf(0, 10), gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(5, 50, 0), GasSwitch(30, 21, 0)))
        assertEquals(listOf(GasSwitch(0, 50, 0), GasSwitch(20, 21, 0)), ActivityMerge.shifted(data, -10).gasSwitches)
    }

    @Test
    fun lapsComeOverWhenTheActivityHasNone() {
        val existing = ActivityStreamData(time = listOf(0, 300, 600), heartRate = listOf(120, 140, 150))
        val other =
            ActivityStreamData(time = listOf(0, 300, 600), laps = listOf(Lap(0, 300, distance = 1200), Lap(300, 300, distance = 1800)))
        assertEquals(
            listOf(Lap(30, 300, distance = 1200), Lap(330, 300, distance = 1800)),
            ActivityMerge.fillMissing(existing, other, 30).laps,
        )
        val own = existing.copy(laps = listOf(Lap(0, 600)))
        assertEquals(own.laps, ActivityMerge.fillMissing(own, other, 30).laps)
        assertEquals(listOf(Lap(0, 300, distance = 1800)), ActivityMerge.shifted(other, -300).laps, "a lap ended before the start goes")
    }

    @Test
    fun aLogbooksTankPressureFillsADiveWithoutIt() {
        val fit = ActivityStreamData(time = listOf(0, 10, 20), depth = listOf(0, 500, 0), ppO2 = listOf(210, 315, 210))
        val uddf = ActivityStreamData(time = listOf(0, 10, 20), tankPressures = listOf(TankPressure(listOf(200_000, 190_000, 180_000))))
        val merged = ActivityMerge.fillMissing(fit, uddf, offsetSeconds = 0, existingTanks = 0, otherTanks = 1)
        assertEquals(uddf.tankPressures, merged.tankPressures, "a dive with no tanks takes the other's")
        assertEquals(fit.ppO2, merged.ppO2)
        assertEquals(uddf.tankPressures, ActivityMerge.fillMissing(fit, uddf, 0, existingTanks = 1, otherTanks = 1).tankPressures)
        assertEquals(
            null,
            ActivityMerge.fillMissing(fit, uddf, 0, existingTanks = 2, otherTanks = 1).tankPressures,
            "they wouldn't line up",
        )
        assertFalse(ActivityMerge.addsChannels(fit, uddf, existingTanks = 2, otherTanks = 1))
        assertTrue(ActivityMerge.addsChannels(fit, uddf, existingTanks = 0, otherTanks = 1))
    }

    @Test
    fun shiftsStreamsOntoAnEarlierStart() {
        val data = ActivityStreamData(time = listOf(0, 10, 20), depth = listOf(1, 2, 3))
        assertEquals(ActivityStreamData(time = listOf(10, 20, 30), depth = listOf(1, 2, 3)), ActivityMerge.shifted(data, 10))
        assertEquals(
            ActivityStreamData(time = listOf(0, 10), depth = listOf(2, 3)),
            ActivityMerge.shifted(data, -10),
            "nothing before the start",
        )
    }
}
