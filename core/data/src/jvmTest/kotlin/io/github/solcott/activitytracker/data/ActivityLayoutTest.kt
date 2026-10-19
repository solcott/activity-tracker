package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityLayout
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How a draft is split by tier (`feed/detail-visibility`: the storage requirements), before anything is written. */
class ActivityLayoutTest {
    private val uri = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3kride")
    private val createdAt = Instant.parse("2026-10-14T15:00:00Z")

    private val streams =
        ActivityStreamData(
            time = listOf(0, 1, 2),
            altitude = listOf(100, 101, 102),
            distance = listOf(0, 5, 10),
            speed = listOf(5, 5, 5),
            heartRate = listOf(120, 130, 140),
            power = listOf(200, 210, 220),
            laps = listOf(Lap(start = 0, elapsed = 3, distance = 10, avgSpeed = 5, avgHeartRate = 130, maxHeartRate = 140, avgPower = 210)),
        )

    /** A ride that started at 06:12 at UTC-7, with heart rate, power, calories, a route and streams. */
    private fun draft(visibility: String, categories: Map<String, String>) =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = "Ride",
            description = null,
            startedAt = Instant.parse("2026-10-14T13:12:00Z"),
            elapsed = 60.minutes,
            distanceMeters = 30_000.0,
            avgHeartRate = 140,
            maxHeartRate = 175,
            avgPowerWatts = 220,
            maxPowerWatts = 600,
            normalizedPowerWatts = 240,
            workJoules = 800_000.0,
            calories = 700,
            streams = streams,
            utcOffsetMinutes = -420,
            visibility = visibility,
            categories = categories,
        )

    private fun split(visibility: String, categories: Map<String, String>, polyline: String? = null): ActivityLayout {
        val draft = draft(visibility, categories)
        return ActivityLayout.split(draft, draft.toRecord(createdAt, polyline = polyline?.let { SegmentedPolyline(it, null) }), uri)
    }

    @Test
    fun publicRideWithFollowersPower() {
        val layout = split(Visibility.PUBLIC, mapOf(DetailCategory.POWER to Visibility.FOLLOWERS))
        with(layout.record) {
            assertNull(avgPower)
            assertNull(maxPower)
            assertNull(normalizedPower)
            assertNull(work)
            assertEquals(140, avgHeartRate)
        }
        assertEquals(setOf(Visibility.FOLLOWERS), layout.details.keys)
        with(layout.details.getValue(Visibility.FOLLOWERS)) {
            assertEquals(220, avgPower)
            assertEquals(600, maxPower)
            assertEquals(240, normalizedPower)
            assertEquals(800L, work)
            assertNull(avgHeartRate)
            assertEquals(uri, activity)
        }
    }

    @Test
    fun twoHiddenTiers() {
        val layout =
            split(Visibility.PUBLIC, mapOf(DetailCategory.CALORIES to Visibility.MUTUALS, DetailCategory.HEART_RATE to Visibility.SELF))
        assertNull(layout.record.calories)
        assertNull(layout.record.avgHeartRate)
        assertEquals(700, layout.details.getValue(Visibility.MUTUALS).calories)
        assertEquals(140, layout.details.getValue(Visibility.SELF).avgHeartRate)
        assertNull(layout.details.getValue(Visibility.SELF).calories)
        assertEquals(listOf(Visibility.SELF, Visibility.MUTUALS, Visibility.PUBLIC), layout.tiersNarrowestFirst)
    }

    @Test
    fun nothingHiddenHasNoDetails() {
        val layout = split(Visibility.FOLLOWERS, DetailCategory.all.associateWith { Visibility.PUBLIC })
        assertEquals(emptyMap(), layout.details)
        assertEquals(setOf(Visibility.FOLLOWERS), layout.streams.keys)
        assertEquals(700, layout.record.calories)
    }

    @Test
    fun hiddenStartKeepsOnlyTheLocalDate() {
        val layout = split(Visibility.PUBLIC, mapOf(DetailCategory.START_TIME to Visibility.SELF))
        // 06:12 at UTC-7: local midnight is 07:00Z.
        assertEquals(Instant.parse("2026-10-14T07:00:00Z"), layout.record.startedAt)
        assertEquals(Instant.parse("2026-10-14T13:12:00Z"), layout.details.getValue(Visibility.SELF).startedAt)
    }

    @Test
    fun hiddenMapLeavesNoPolylineButKeepsTheElevationChart() {
        val layout = split(Visibility.PUBLIC, mapOf(DetailCategory.MAP to Visibility.SELF), polyline = "_p~iF~ps|U")
        assertNull(layout.record.polyline)
        assertEquals("_p~iF~ps|U", layout.details.getValue(Visibility.SELF).polyline)
        assertEquals(streams.altitude, layout.streams.getValue(Visibility.PUBLIC).altitude, "elevation is core")
        assertEquals(setOf(Visibility.PUBLIC), layout.streams.keys, "a map has no channels")
    }

    @Test
    fun streamsAndLapsSplitByTierWithTheCoreAtTheActivity() {
        val layout = split(Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))
        val public = layout.streams.getValue(Visibility.PUBLIC)
        assertNull(public.heartRate)
        assertEquals(streams.speed, public.speed)
        assertEquals(streams.distance, public.distance)
        assertEquals(Lap(start = 0, elapsed = 3, distance = 10, avgSpeed = 5, avgPower = 210), public.laps?.single())
        val self = layout.streams.getValue(Visibility.SELF)
        assertEquals(listOf(StreamChannel.HEART_RATE, StreamChannel.LAPS), self.channels)
        assertEquals(Lap(start = 0, elapsed = 3, avgHeartRate = 130, maxHeartRate = 140), self.laps?.single())
    }

    @Test
    fun everyCategoryOnlyMeOnAPublicActivityKeepsTheCorePublic() {
        val layout = split(Visibility.PUBLIC, DetailCategory.all.associateWith { Visibility.SELF })
        with(layout.record) {
            assertEquals(30_000L, distance)
            assertEquals(3600L, elapsedTime)
            assertNull(avgSpeed)
            assertNull(avgHeartRate)
            assertNull(calories)
        }
        val public = assertNotNull(layout.streams[Visibility.PUBLIC])
        assertEquals(listOf(StreamChannel.DISTANCE, StreamChannel.ALTITUDE, StreamChannel.LAPS), public.channels)
        assertTrue(layout.streams.getValue(Visibility.SELF).channels.containsAll(listOf(StreamChannel.SPEED, StreamChannel.HEART_RATE)))
    }

    @Test
    fun categoriesWiderThanTheActivityAreCapped() {
        val layout = split(Visibility.MUTUALS, mapOf(DetailCategory.HEART_RATE to Visibility.PUBLIC))
        assertEquals(emptyMap(), layout.details)
        assertEquals(setOf(Visibility.MUTUALS), layout.streams.keys)
    }
}
