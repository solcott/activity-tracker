package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityMerge
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.Sport
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
