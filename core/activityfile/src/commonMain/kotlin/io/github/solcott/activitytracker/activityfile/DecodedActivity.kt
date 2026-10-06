package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.LocalDateTime

/**
 * The FIT messages an import needs, already scaled to SI units by the SDK (positions in degrees, see [FitUnits]). Times are epoch
 * milliseconds and enum values are the SDK's names, in whatever case the SDK spells them ("RUNNING", "running", "eBiking"):
 * [DecodedActivityMapper] normalizes both.
 */
data class DecodedActivity(
    val fileId: DecodedSource?,
    val sessions: List<DecodedSummary>,
    val laps: List<DecodedSummary>,
    val records: List<DecodedSample>,
    /** Dive computer header values; null unless the file is a dive log. */
    val dive: DecodedDive? = null,
    /** The start as the device's clock read it, set only when the file's times have no zone (Shearwater logs). */
    val localStart: LocalDateTime? = null,
)

data class DecodedSource(
    /** File type; an activity file is "activity". */
    val type: String?,
    val manufacturer: String?,
    val product: String?,
    val timeCreatedMillis: Long?,
)

/** A session or lap message. */
data class DecodedSummary(
    val sport: String?,
    val subSport: String?,
    val startTimeMillis: Long?,
    val totalElapsedSeconds: Double?,
    /** Timer time, which excludes auto-pauses: what we call moving time. */
    val totalTimerSeconds: Double?,
    val totalDistanceMeters: Double?,
    val totalAscentMeters: Double?,
    val avgSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgPowerWatts: Int?,
    val avgCadence: Int?,
    val avgTemperatureCelsius: Double?,
)

/** One sample of the activity's record stream. */
data class DecodedSample(
    val timestampMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val heartRate: Int?,
    val cadence: Int?,
    val powerWatts: Int?,
    val speedMps: Double?,
    val distanceMeters: Double?,
    val temperatureCelsius: Double?,
    /** Depth below the surface, for dives. */
    val depthMeters: Double? = null,
)

/** A dive log's header, in SI units. */
data class DecodedDive(
    val maxDepthMeters: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
    /** Time at the surface since the previous dive. */
    val surfaceIntervalMinutes: Int?,
    /** Bühlmann gradient factors, in percent. */
    val gradientFactorLow: Int?,
    val gradientFactorHigh: Int?,
    /** Central nervous system oxygen toxicity, in percent of the limit. */
    val startCns: Int?,
    val endCns: Int?,
    /** Each gas breathed, in order of first use. */
    val gases: List<DiveGas>,
    /** Breathed from a closed-circuit rebreather for at least part of the dive. */
    val closedCircuit: Boolean,
    /** The dive computer's own average; null to compute it from the depth profile. */
    val avgDepthMeters: Double? = null,
    /** Time from leaving the surface to the start of the final ascent. */
    val bottomTimeSeconds: Double? = null,
    /** Whole-body oxygen exposure, in oxygen toxicity units. */
    val oxygenToxicityOtu: Int? = null,
    /** Air consumption by volume (respiratory minute volume), which needs the tank's size. */
    val rmvLitersPerMinute: Double? = null,
    /** Air consumption by tank pressure, adjusted to the surface. */
    val sacBarPerMinute: Double? = null,
    /** One per tank a pressure transmitter was on. */
    val tanks: List<DiveTank> = emptyList(),
    /** Where the diver went in and came out, if the computer had a GPS fix at the surface. */
    val entry: DivePosition? = null,
    val exit: DivePosition? = null,
)

/** A tank's pressures, in bar (the diving convention), and the gas used, in liters at the surface when the tank's size was set. */
data class DiveTank(val startPressureBar: Double?, val endPressureBar: Double?, val volumeUsedLiters: Double?)

data class DivePosition(val latitude: Double, val longitude: Double)

/** A breathing gas, as whole percents. Air is 21/0. */
data class DiveGas(val oxygenPercent: Int, val heliumPercent: Int)

object FitUnits {
    /** FIT stores positions as semicircles: 2^31 semicircles = 180°. */
    private const val DEGREES_PER_SEMICIRCLE = 180.0 / 2_147_483_648.0

    fun semicirclesToDegrees(semicircles: Int): Double = semicircles * DEGREES_PER_SEMICIRCLE
}
