package io.github.solcott.activitytracker.activityfile

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
    val exit: DivePosition? = null,
)

data class DepthPoint(val seconds: Long, val depthMeters: Double)
