package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A ride sampled every 10 s: 6 min standing in the car park, 60 min riding at 8 m/s (climbing 100 m over the first half, then back down), 9
 * min standing at the car, then 4 min driving at 13.6 m/s.
 */
internal object TrimFixtures {
    val start: Instant = Instant.parse("2026-11-22T07:36:00Z")
    const val STANDING_START = 360
    const val RIDE_END = STANDING_START + 3600
    const val DRIVE_START = RIDE_END + 540
    const val END = DRIVE_START + 240
    const val RIDE_MPS = 8.0
    const val DRIVE_MPS = 13.6

    /** Meters per second at [t] seconds. */
    fun speedAt(t: Int): Double =
        when {
            t < STANDING_START -> 0.0
            t < RIDE_END -> RIDE_MPS
            t < DRIVE_START -> 0.0
            else -> DRIVE_MPS
        }

    val times: List<Int> = (0..END step 10).toList()

    /** Meters from the start at each sample. */
    val meters: List<Double> = run {
        var d = 0.0
        times.mapIndexed { i, t ->
            if (i > 0) d += speedAt(times[i - 1]) * (t - times[i - 1])
            d
        }
    }

    /** Meters of altitude at each sample. */
    val altitude: List<Double> = times.map { t ->
        when {
            t < STANDING_START -> 200.0
            t < STANDING_START + 1800 -> 200.0 + 100.0 * (t - STANDING_START) / 1800
            t < RIDE_END -> 300.0 - 100.0 * (t - STANDING_START - 1800) / 1800
            else -> 200.0
        }
    }

    val streams: ActivityStreamData =
        ActivityStreamData(
            time = times,
            distance = meters.map { (it * 100).roundToInt() },
            altitude = altitude.map { (it * 100).roundToInt() },
            speed = times.map { (speedAt(it) * 1000).roundToInt() },
            heartRate = times.map { if (speedAt(it) == RIDE_MPS) 150 else 90 },
            power = times.map { if (speedAt(it) == RIDE_MPS) 200 else 0 },
        )

    /** Points heading north at the recorded speed. */
    val route: List<LocationSample> = times.mapIndexed { i, t ->
        LocationSample(45.0 + meters[i] / METERS_PER_DEGREE, 7.0, altitude[i], speedAt(t), 5.0, start + t.seconds)
    }

    /** The device's own totals, a little off the streams', as devices are. */
    const val DEVICE_DISTANCE = 32_120L
    const val DEVICE_MOVING = 3_860L
    const val CALORIES = 1_000

    val record: ActivityRecord =
        ActivityRecord(
            sport = Sport.RIDE,
            startedAt = start,
            elapsedTime = END.toLong(),
            createdAt = start,
            movingTime = DEVICE_MOVING,
            distance = DEVICE_DISTANCE,
            elevationGain = 100,
            elevationLoss = 100,
            avgSpeed = DEVICE_DISTANCE * 1000 / DEVICE_MOVING,
            maxSpeed = (DRIVE_MPS * 1000).roundToLong(),
            avgHeartRate = 140,
            maxHeartRate = 150,
            avgPower = 180,
            maxPower = 200,
            calories = CALORIES,
        )

    val input: TrimInput = TrimInput(start, END, streams, route)

    fun draft(withRoute: Boolean = true): ActivityDraft =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = "Sunday loop",
            description = null,
            startedAt = start,
            elapsed = END.seconds,
            movingSeconds = DEVICE_MOVING,
            distanceMeters = DEVICE_DISTANCE.toDouble(),
            elevationGainMeters = 100.0,
            elevationLossMeters = 100.0,
            avgHeartRate = 140,
            maxHeartRate = 150,
            calories = CALORIES,
            route = if (withRoute) route else emptyList(),
            gpx = if (withRoute) byteArrayOf(1) else null,
            streams = streams,
        )

    private const val METERS_PER_DEGREE = 111_195.0
}
