package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Turns decoded FIT messages into an [ImportedActivity]. Session totals win because the device computed them from data we don't see
 * (auto-pause, barometric altitude); anything a session leaves out is computed from the record stream. A multi-session file (a triathlon,
 * say) imports as one activity with the first session's sport.
 */
object FitActivityMapper {
    fun map(fit: FitActivity): ImportedActivity {
        val type = fit.fileId?.type?.fitKey()
        if (type != null && type != "activity") activityFileError("Not an activity file: ${fit.fileId.type}")

        val records = fit.records.filter { it.timestampMillis != null }.sortedBy { it.timestampMillis }
        val sessions = fit.sessions
        if (sessions.isEmpty() && records.isEmpty()) activityFileError("No activity data in file")

        val first = sessions.firstOrNull()
        val startedAtMillis =
            first?.startTimeMillis
                ?: records.firstOrNull()?.timestampMillis
                ?: fit.fileId?.timeCreatedMillis
                ?: activityFileError("No start time in file")
        val samples = records.toSamples()

        return ImportedActivity(
            sport = sport(first?.sport, first?.subSport),
            startedAt = Instant.fromEpochMilliseconds(startedAtMillis),
            elapsed = elapsed(sessions, records),
            movingSeconds = sessions.total { it.totalTimerSeconds }?.roundToLong(),
            distanceMeters = distanceMeters(sessions, records, samples),
            elevationGainMeters = sessions.total { it.totalAscentMeters } ?: records.mapNotNull { it.altitudeMeters }.climb(),
            maxSpeedMps = sessions.mapNotNull { it.maxSpeedMps }.maxOrNull() ?: records.mapNotNull { it.speedMps }.maxOrNull(),
            avgHeartRate = sessions.weightedAverage { it.avgHeartRate?.toDouble() }?.roundToInt() ?: records.averageOf { it.heartRate },
            maxHeartRate = sessions.mapNotNull { it.maxHeartRate }.maxOrNull() ?: records.mapNotNull { it.heartRate }.maxOrNull(),
            avgTemperatureCelsius =
                sessions.weightedAverage { it.avgTemperatureCelsius }
                    ?: records.mapNotNull { it.temperatureCelsius }.takeIf { it.isNotEmpty() }?.average(),
            device = fit.fileId?.let { device(it.manufacturer, it.product) },
            samples = samples,
        )
    }

    /** [records] are sorted and all have a timestamp. */
    private fun elapsed(sessions: List<FitSummary>, records: List<FitRecord>): Duration {
        val first = records.firstOrNull()?.timestampMillis ?: 0L
        val last = records.lastOrNull()?.timestampMillis ?: 0L
        return sessions.total { it.totalElapsedSeconds }?.seconds ?: (last - first).milliseconds
    }

    private fun distanceMeters(sessions: List<FitSummary>, records: List<FitRecord>, samples: List<LocationSample>): Double? =
        sessions.total { it.totalDistanceMeters }
            ?: records.lastNotNullOf { it.distanceMeters }
            ?: samples.zipWithNext(TrackMath::distanceMeters).sum().takeIf { samples.size >= 2 }

    /** Maps a FIT sport (and sub-sport) to a lexicon sport. */
    fun sport(sport: String?, subSport: String? = null): String =
        when (sport?.fitKey()) {
            "running" -> Sport.RUN
            "cycling",
            "ebiking" -> Sport.RIDE
            "walking" -> Sport.WALK
            "hiking",
            "mountaineering" -> Sport.HIKE
            "swimming" -> Sport.SWIM
            "alpineskiing",
            "crosscountryskiing",
            "snowboarding" -> Sport.SKI
            else ->
                when (subSport?.fitKey()) {
                    "indoorcycling",
                    "virtualactivity" -> Sport.RIDE
                    "treadmill" -> Sport.RUN
                    else -> Sport.OTHER
                }
        }

    /** "garmin" + "fenix7" → "Garmin fenix7"; FIT names are snake_case in Java, camelCase in JS. */
    private fun device(manufacturer: String?, product: String?): String? =
        listOfNotNull(
                manufacturer?.takeUnless { it.fitKey() in setOf("unknown", "invalid") }?.humanize()?.replaceFirstChar { it.uppercase() },
                product?.humanize(),
            )
            .joinToString(" ")
            .ifBlank { null }

    private fun List<FitRecord>.toSamples(): List<LocationSample> = mapNotNull { r ->
        val lat = r.latitude ?: return@mapNotNull null
        val lon = r.longitude ?: return@mapNotNull null
        LocationSample(lat, lon, r.altitudeMeters, r.speedMps, accuracy = null, time = Instant.fromEpochMilliseconds(r.timestampMillis!!))
    }
}

/** Same as the recorder's: altitude only counts as climb once it moves more than this, so sensor noise doesn't add up. */
private const val ELEVATION_HYSTERESIS_M = 3.0

private fun List<Double>.climb(): Double? {
    if (isEmpty()) return null
    var base = first()
    var gain = 0.0
    for (altitude in drop(1)) {
        if (abs(altitude - base) > ELEVATION_HYSTERESIS_M) {
            if (altitude > base) gain += altitude - base
            base = altitude
        }
    }
    return gain
}

/** Null unless at least one session has the value. */
private inline fun List<FitSummary>.total(value: (FitSummary) -> Double?): Double? = mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

/** Average across sessions, weighted by timer time (falling back to equal weights). */
private inline fun List<FitSummary>.weightedAverage(value: (FitSummary) -> Double?): Double? {
    val pairs = mapNotNull { s -> value(s)?.let { it to (s.totalTimerSeconds ?: 1.0) } }
    val weight = pairs.sumOf { it.second }
    return if (pairs.isEmpty() || weight <= 0) null else pairs.sumOf { it.first * it.second } / weight
}

private inline fun List<FitRecord>.averageOf(value: (FitRecord) -> Int?): Int? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private inline fun <T : Any> List<FitRecord>.lastNotNullOf(value: (FitRecord) -> T?): T? = asReversed().firstNotNullOfOrNull(value)

private fun String.fitKey() = filter { it.isLetterOrDigit() }.lowercase()

private fun String.humanize() =
    replace('_', ' ').replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().takeUnless { it.isBlank() || it == "unknown" }
