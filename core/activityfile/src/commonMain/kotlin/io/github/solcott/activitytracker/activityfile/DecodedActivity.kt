package io.github.solcott.activitytracker.activityfile

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
)

/** A breathing gas, as whole percents. Air is 21/0. */
data class DiveGas(val oxygenPercent: Int, val heliumPercent: Int)

object FitUnits {
    /** FIT stores positions as semicircles: 2^31 semicircles = 180°. */
    private const val DEGREES_PER_SEMICIRCLE = 180.0 / 2_147_483_648.0

    fun semicirclesToDegrees(semicircles: Int): Double = semicircles * DEGREES_PER_SEMICIRCLE
}
