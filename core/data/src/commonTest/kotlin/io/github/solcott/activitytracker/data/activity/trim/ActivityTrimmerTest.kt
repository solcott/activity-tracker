package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.DEVICE_DISTANCE
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.DEVICE_MOVING
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.DRIVE_START
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.RIDE_END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.STANDING_START
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.Gpx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ActivityTrimmerTest {
    private val record = TrimFixtures.record
    private val input = TrimFixtures.input

    @Test
    fun gpsRideLosesTheCarParkAndTheDrive() {
        val span = KeptSpan(STANDING_START, RIDE_END)
        val trimmed = ActivityTrimmer.record(record, input, span)

        assertEquals(TrimFixtures.start + STANDING_START.seconds, trimmed.startedAt)
        assertEquals((RIDE_END - STANDING_START).toLong(), trimmed.elapsedTime)
        // The device's distance less the measured drive (240 s at 13.6 m/s).
        assertEquals(DEVICE_DISTANCE - 3264, trimmed.distance)
        // Standing counts no moving time and the drive's 240 s does; the device's figure, 20 s over, can't exceed what's kept.
        assertEquals(minOf(DEVICE_MOVING - 240, (RIDE_END - STANDING_START).toLong()), trimmed.movingTime)
        assertEquals(150, trimmed.maxHeartRate)
        assertEquals(150.0, trimmed.avgHeartRate!!.toDouble(), 1.0)
        assertEquals(8000L, trimmed.maxSpeed)
        assertEquals(100L, trimmed.elevationGain)
    }

    @Test
    fun aOneMinuteTrimMovesDistanceOnlyByTheCut() {
        val span = KeptSpan(0, RIDE_END - 60)
        val trimmed = ActivityTrimmer.record(record, input, span)
        // 60 s at 8 m/s, plus the stop (no distance) and the drive.
        assertEquals(DEVICE_DISTANCE - 480 - 3264, trimmed.distance)
    }

    @Test
    fun caloriesScaleWithKeptMovingTime() {
        val span = KeptSpan(STANDING_START, RIDE_END)
        val trimmed = ActivityTrimmer.record(record, input, span)
        val share = (RIDE_END - STANDING_START).toDouble() / DEVICE_MOVING
        assertEquals(TrimFixtures.CALORIES * share, trimmed.calories!!.toDouble(), 1.0)
    }

    @Test
    fun streamsAreRebasedToTheKeptStart() {
        val span = KeptSpan(STANDING_START, RIDE_END)
        val streams = ActivityTrimmer.streams(TrimFixtures.streams, span)
        assertEquals(0, streams.time.first())
        assertEquals(RIDE_END - STANDING_START, streams.time.last())
        assertEquals(streams.time.size, streams.heartRate!!.size)
        assertEquals(streams.time.size, streams.altitude!!.size)
    }

    @Test
    fun wholeSpanChangesNothing() {
        assertSame(record, ActivityTrimmer.record(record, input, KeptSpan.whole(END)))
    }

    @Test
    fun draftKeepsItsRouteAndTrackWithinTheSpan() {
        val span = KeptSpan(STANDING_START, RIDE_END)
        val trimmed = TrimFixtures.draft().trimmed(span)
        assertEquals(TrimFixtures.start + STANDING_START.seconds, trimmed.startedAt)
        assertEquals((RIDE_END - STANDING_START).seconds, trimmed.elapsed)
        assertTrue(trimmed.route.all { it.time >= trimmed.startedAt && it.time <= trimmed.startedAt + trimmed.elapsed })
        val gpx = assertNotNull(trimmed.gpx).decodeToString()
        assertEquals(Gpx.write("Sunday loop", Sport.RIDE, trimmed.route), gpx)
        assertEquals(DEVICE_DISTANCE.toDouble() - 3264, trimmed.distanceMeters!!, 0.5)
    }

    @Test
    fun indoorRideTrimsByItsStreams() {
        val trimmed = TrimFixtures.draft(withRoute = false).trimmed(KeptSpan(STANDING_START, DRIVE_START))
        // Ending where the drive starts cuts it.
        assertEquals(DEVICE_DISTANCE.toDouble() - 3264, trimmed.distanceMeters!!, 0.5)
        assertNull(trimmed.gpx)
        assertEquals(0, trimmed.streams!!.time.first())
    }

    @Test
    fun manualEntryOnlyMovesItsTimes() {
        val manual =
            ActivityDraft(
                type = ActivityType(Sport.RUN),
                title = null,
                description = null,
                startedAt = TrimFixtures.start,
                elapsed = 100.minutes,
                movingSeconds = 95 * 60,
                distanceMeters = 20_000.0,
            )
        val trimmed = manual.trimmed(KeptSpan(5 * 60, 90 * 60))
        assertEquals(TrimFixtures.start + 5.minutes, trimmed.startedAt)
        assertEquals(85.minutes, trimmed.elapsed)
        assertEquals(20_000.0, trimmed.distanceMeters)
        assertEquals(85L * 60, trimmed.movingSeconds)
    }

    @Test
    fun poolSwimDropsLengthsPastTheEnd() {
        val lengths = (0 until 40).map { Length(start = it * 60, elapsed = 50) }
        val data = ActivityStreamData(time = (0..2400 step 10).toList(), lengths = lengths)
        val trimmed = ActivityTrimmer.streams(data, KeptSpan(0, 2155))
        // The length starting at 2100 ends at 2150, inside; the one starting at 2160 is gone.
        assertEquals(36, trimmed.lengths!!.size)
    }

    @Test
    fun lapAcrossTheEndIsCutThereAndRecomputed() {
        val laps = listOf(Lap(start = 0, elapsed = 2000, distance = 99_999, calories = 400), Lap(start = 2000, elapsed = END - 2000))
        val data = TrimFixtures.streams.copy(laps = laps)
        val trimmed = ActivityTrimmer.streams(data, KeptSpan(STANDING_START, 1500))
        val lap = trimmed.laps!!.single()
        assertEquals(0, lap.start)
        assertEquals(1500 - STANDING_START, lap.elapsed)
        assertEquals(((1500 - STANDING_START) * TrimFixtures.RIDE_MPS).toInt(), lap.distance)
        assertEquals(1500 - STANDING_START, lap.moving)
        assertEquals(400.0 * (1500 - STANDING_START) / 2000, lap.calories!!.toDouble(), 1.0)
    }

    @Test
    fun aShortChannelIsCutWithTheRest() {
        val short = input.streams!!.let { it.copy(cadence = List(it.time.size - 5) { 80 }) }
        val span = KeptSpan(STANDING_START, END)
        val streams = ActivityTrimmer.streams(short, span)
        assertEquals(streams.time.size, streams.cadence?.size)
    }
}
