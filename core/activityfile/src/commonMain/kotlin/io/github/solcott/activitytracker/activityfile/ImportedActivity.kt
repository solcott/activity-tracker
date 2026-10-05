package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Duration
import kotlin.time.Instant

/** An activity read from a file, whatever its format. Units match `ActivityDraft`: SI, as Doubles. */
data class ImportedActivity(
    /** The file's sport and sub-sport, as lexicon values. */
    val type: ActivityType,
    val startedAt: Instant,
    val elapsed: Duration,
    val movingSeconds: Long?,
    val distanceMeters: Double?,
    val elevationGainMeters: Double?,
    val maxSpeedMps: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgTemperatureCelsius: Double?,
    /** Recording device, e.g. "Garmin Fenix7". */
    val device: String?,
    /** Track points that have a position, in time order. Empty for indoor activities. */
    val samples: List<LocationSample>,
)
