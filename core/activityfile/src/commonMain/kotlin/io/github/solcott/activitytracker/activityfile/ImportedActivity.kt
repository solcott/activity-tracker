package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
    /** What a dive computer logged; null for everything else. */
    val dive: ImportedDive? = null,
    /**
     * The start as the recording device's clock read it, set only when the file's times have no zone (dive computers keep local time).
     * [startedAt] reads it in this device's zone, which is wrong for a dive logged elsewhere: [inZone] reads it in the right one.
     */
    val localStart: LocalDateTime? = null,
    /** The name the file gave the activity ("Morning Ride"); null when it has none. */
    val title: String? = null,
    val elevationLossMeters: Double? = null,
    /** Revolutions (or strides) per minute; the average leaves out time spent coasting at zero. */
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    /** Null without a power meter. */
    val power: ImportedPower? = null,
    /** Null without electronic shifting. */
    val shifting: ImportedShifting? = null,
    /** Garmin's mountain bike dynamics; null when the device didn't record them. */
    val mtb: ImportedMtb? = null,
    /** The sensors paired with the recording device, in the order the file lists them. */
    val sensors: List<ImportedSensor> = emptyList(),
    /** The athlete's thresholds as the recording device had them set (FIT only). */
    val deviceThresholds: DeviceThresholds? = null,
    /** Every sample's channels, indoor ones included, for charts; null when the file has none. Times are relative to [startedAt]. */
    val streams: ActivityStreamData? = null,
) {
    /** This activity with [localStart] read in [zone], every time moved together. Unchanged if the file's times had a zone. */
    fun inZone(zone: TimeZone): ImportedActivity {
        val shift = localStart?.toInstant(zone)?.minus(startedAt) ?: Duration.ZERO
        return if (shift == Duration.ZERO) this
        else copy(startedAt = startedAt + shift, samples = samples.map { it.copy(time = it.time + shift) })
    }
}

/** A dive's depths, gases and decompression state, in SI units. */
data class ImportedDive(
    val maxDepthMeters: Double?,
    /** The dive computer's, else time-weighted over the samples below the surface. */
    val avgDepthMeters: Double?,
    /** The coldest water, usually at depth. */
    val minWaterTempCelsius: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
    val surfaceIntervalMinutes: Int?,
    val gradientFactorLow: Int?,
    val gradientFactorHigh: Int?,
    val startCns: Int?,
    val endCns: Int?,
    /** In order of first use. */
    val gases: List<DiveGas>,
    val closedCircuit: Boolean,
    /** Depth over time, from the start of the dive. */
    val profile: List<DepthPoint>,
    val bottomTimeSeconds: Double? = null,
    val oxygenToxicityOtu: Int? = null,
    val rmvLitersPerMinute: Double? = null,
    val sacBarPerMinute: Double? = null,
    val tanks: List<DiveTank> = emptyList(),
    val entry: DivePosition? = null,
    /** The dive site's name, from a logbook that names it. Unlike [entry], it goes into the public record. */
    val siteName: String? = null,
    val exit: DivePosition? = null,
)

data class DepthPoint(val seconds: Long, val depthMeters: Double)

data class ImportedPower(
    val avgWatts: Int?,
    val maxWatts: Int?,
    /** What the effort cost the body, weighting surges over steady riding: 30 s rolling averages, then their 4th-power mean. */
    val normalizedWatts: Int?,
    val workJoules: Double?,
    /** The athlete's FTP as the device had it; [intensityFactor] and [trainingStressScore] are relative to it. */
    val ftpWatts: Int?,
    /** Normalized power over FTP. */
    val intensityFactor: Double?,
    val trainingStressScore: Double?,
)

data class ImportedShifting(
    val frontShifts: Int?,
    val rearShifts: Int?,
    /** Chainrings used, in teeth as the device's bike profile has them, largest first. */
    val chainrings: List<Int>,
    /** Cogs used, in teeth as the device's bike profile has them, smallest first. */
    val cogs: List<Int>,
    /** Time in each rear gear, from each shift to the next, by gear number (1 is the largest cog). */
    val timeInGears: List<GearTime>,
)

/**
 * [gear] is the derailleur's. [teeth] come from the device's gearing setup, which is often a default (a 39T ring and 10–26T cassette on a
 * 10–52T mountain bike), so the screens use [gear].
 */
data class GearTime(val gear: Int, val teeth: Int?, val seconds: Long)

data class ImportedMtb(
    val jumps: Int?,
    val longestHangTimeSeconds: Double?,
    val longestJumpMeters: Double?,
    /** Garmin's trail difficulty score. */
    val grit: Double?,
    /** Garmin's score for how smoothly the trail was ridden; lower is smoother. */
    val flow: Double?,
)

/** A sensor paired with the recording device. [model] is null when only its maker can name it (a bare product number). */
data class ImportedSensor(
    val kind: SensorKind,
    val manufacturer: String?,
    val model: String?,
    val battery: SensorBattery?,
    val batteryPercent: Int?,
)
