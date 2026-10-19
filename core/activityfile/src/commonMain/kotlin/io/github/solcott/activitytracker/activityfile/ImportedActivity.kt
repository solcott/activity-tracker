package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
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
    /** The recording device and paired sensors with what tells them apart, kept privately; null when the file names no device. */
    val devices: ImportedDevices? = null,
    /** The athlete's thresholds as the recording device had them set (FIT only). */
    val deviceThresholds: DeviceThresholds? = null,
    /** Every sample's channels, indoor ones included, for charts; null when the file has none. Times are relative to [startedAt]. */
    val streams: ActivityStreamData? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    /** The device's aerobic and anaerobic training effect, 0 to 5. */
    val aerobicTrainingEffect: Double? = null,
    val anaerobicTrainingEffect: Double? = null,
    /** The file's own notes on the activity (a logbook's), for the import screen to start the description from. */
    val description: String? = null,
    /** A pool swim's pool length, in centimeters (a 25 yd pool is 2286); null for any other activity. */
    val poolLengthCentimeters: Int? = null,
    /** The RMSSD of the file's beat-to-beat intervals, in milliseconds ([Hrv]); stored with the training load, never on the record. */
    val hrvRmssd: Int? = null,
    /** The device's cycle count, a run's strides doubled to steps (see `ActivityRecord.cycles`). */
    val cycles: Int? = null,
    val avgStrokeDistanceCentimeters: Int? = null,
    /**
     * The UTC offset where the activity started, in minutes east of UTC: the FIT activity message's, or for a file whose times have no
     * zone, the zone [inZone] placed it in (the importing device's until then).
     */
    val utcOffsetMinutes: Int? = null,
) {
    /**
     * This activity with [localStart] read in [zone], every time moved together, and that zone's offset at the start as its UTC offset.
     * Unchanged if the file's times had a zone.
     */
    fun inZone(zone: TimeZone): ImportedActivity {
        val local = localStart ?: return this
        val placed = local.toInstant(zone)
        val shift = placed - startedAt
        val offset = zone.offsetAt(placed).totalSeconds / SECONDS_PER_MINUTE
        return if (shift == Duration.ZERO && offset == utcOffsetMinutes) this
        else copy(startedAt = placed, samples = samples.map { it.copy(time = it.time + shift) }, utcOffsetMinutes = offset)
    }
}

private const val SECONDS_PER_MINUTE = 60

/** Where this activity's clock was: its UTC offset as a fixed zone, else [fallback] (the zone the athlete placed a zone-less file in). */
fun ImportedActivity.localZone(fallback: TimeZone): TimeZone =
    utcOffsetMinutes?.let { FixedOffsetTimeZone(UtcOffset(seconds = it * SECONDS_PER_MINUTE)) } ?: fallback

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
    /** Nitrogen loading, in percent of the limit. */
    val startN2: Int? = null,
    val endN2: Int? = null,
    val avgAscentRateMps: Double? = null,
    val maxAscentRateMps: Double? = null,
    val avgDescentRateMps: Double? = null,
    val maxDescentRateMps: Double? = null,
    val descentTimeSeconds: Double? = null,
    /** A `WaterType` token. */
    val waterType: String? = null,
    val waterDensityKgPerM3: Double? = null,
    val airTemperatureCelsius: Double? = null,
    val visibilityMeters: Double? = null,
    val altitudeMeters: Double? = null,
    val noFlyTimeSeconds: Double? = null,
    val desaturationTimeSeconds: Double? = null,
    val highestPpO2Bar: Double? = null,
    /** A `DecoModel` token. */
    val decoModel: String? = null,
    /** The dive computer's ppO₂ limits. */
    val ppO2WarnBar: Double? = null,
    val ppO2CriticalBar: Double? = null,
    val ppO2DecoBar: Double? = null,
    /** A rebreather's setpoints. */
    val lowSetpointBar: Double? = null,
    val highSetpointBar: Double? = null,
    /** The safety stop the computer was set for; null when it was off. */
    val safetyStopSeconds: Int? = null,
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

/**
 * The recording device and the sensors a file names ([FitDevices]), with their identifiers: private, unlike [ImportedActivity.sensors].
 * [activityProfile] is the device's activity profile ("MOUNTAIN").
 */
data class ImportedDevices(val devices: List<ImportedDevice>, val activityProfile: String? = null)

/**
 * A recording device ([recorder]) or a paired sensor. [connection] is a `feed.activityDevices#device` connection token, [softwareVersion]
 * as the device shows it ("31.33"), [batteryMillivolts] and [operatingSeconds] (its lifetime total) as the activity ended.
 */
data class ImportedDevice(
    val recorder: Boolean,
    val kind: SensorKind? = null,
    val manufacturer: String? = null,
    /** Its model name, else the file's product number. */
    val model: String? = null,
    val serialNumber: String? = null,
    val antDeviceNumber: Int? = null,
    val antTransmissionType: Int? = null,
    val connection: String? = null,
    val softwareVersion: String? = null,
    val battery: SensorBattery? = null,
    val batteryPercent: Int? = null,
    val batteryMillivolts: Int? = null,
    val operatingSeconds: Long? = null,
)
