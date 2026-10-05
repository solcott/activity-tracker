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
 * Turns a decoded FIT, GPX or TCX file into an [ImportedActivity]. Summary totals win because the device computed them from data we don't
 * see (auto-pause, barometric altitude): FIT sessions, else laps (all TCX has). Anything the summaries leave out, which is everything for
 * GPX, is computed from the samples. A multi-session file (a triathlon, say) imports as one activity with the first session's sport.
 */
object DecodedActivityMapper {
    fun map(fit: DecodedActivity): ImportedActivity {
        val type = fit.fileId?.type?.fitKey()
        if (type != null && type != "activity") activityFileError("Not an activity file: ${fit.fileId.type}")

        val records = fit.records.filter { it.timestampMillis != null }.sortedBy { it.timestampMillis }
        val sessions = fit.sessions.ifEmpty { fit.laps }
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
            movingSeconds = (sessions.total { it.totalTimerSeconds } ?: samples.movingSeconds())?.roundToLong(),
            distanceMeters = distanceMeters(sessions, records, samples),
            elevationGainMeters = sessions.total { it.totalAscentMeters } ?: records.mapNotNull { it.altitudeMeters }.climb(),
            maxSpeedMps = sessions.mapNotNull { it.maxSpeedMps }.maxOrNull() ?: records.mapNotNull { it.speedMps }.maxOrNull(),
            avgHeartRate = sessions.weightedAverage { it.avgHeartRate?.toDouble() }?.roundToInt() ?: records.averageOf { it.heartRate },
            maxHeartRate = sessions.mapNotNull { it.maxHeartRate }.maxOrNull() ?: records.mapNotNull { it.heartRate }.maxOrNull(),
            avgTemperatureCelsius = avgTemperature(sessions, records),
            device = fit.fileId?.let { device(it.manufacturer, it.product) },
            samples = samples,
        )
    }

    private fun avgTemperature(sessions: List<DecodedSummary>, records: List<DecodedSample>): Double? =
        sessions.weightedAverage { it.avgTemperatureCelsius }
            ?: records.mapNotNull { it.temperatureCelsius }.takeIf { it.isNotEmpty() }?.average()

    /** [records] are sorted and all have a timestamp. */
    private fun elapsed(sessions: List<DecodedSummary>, records: List<DecodedSample>): Duration {
        val first = records.firstOrNull()?.timestampMillis ?: 0L
        val last = records.lastOrNull()?.timestampMillis ?: 0L
        return sessions.total { it.totalElapsedSeconds }?.seconds ?: (last - first).milliseconds
    }

    private fun distanceMeters(sessions: List<DecodedSummary>, records: List<DecodedSample>, samples: List<LocationSample>): Double? =
        sessions.total { it.totalDistanceMeters }
            ?: records.lastNotNullOf { it.distanceMeters }
            ?: samples.zipWithNext(TrackMath::distanceMeters).sum().takeIf { samples.size >= 2 }

    /** Maps a sport name in any format's spelling (FIT enum, TCX `Sport`, GPX `type`), or else a FIT sub-sport, to a lexicon sport. */
    fun sport(sport: String?, subSport: String? = null): String =
        sport?.fitKey()?.let(SPORTS::get) ?: subSport?.fitKey()?.let(SUB_SPORTS::get) ?: Sport.OTHER

    private val SPORTS =
        mapOf(
                Sport.RUN to listOf("running", "run", "trailrunning", "virtualrun", "treadmillrunning"),
                Sport.RIDE to
                    listOf(
                        "cycling",
                        "biking",
                        "ride",
                        "ebiking",
                        "ebikeride",
                        "roadbiking",
                        "mountainbiking",
                        "mountainbikeride",
                        "gravelcycling",
                        "gravelride",
                        "virtualride",
                    ),
                Sport.WALK to listOf("walking", "walk"),
                Sport.HIKE to listOf("hiking", "hike", "mountaineering"),
                Sport.SWIM to listOf("swimming", "swim", "openwaterswimming", "lapswimming"),
                Sport.SKI to
                    listOf(
                        "alpineskiing",
                        "alpineski",
                        "crosscountryskiing",
                        "nordicski",
                        "backcountryskiing",
                        "backcountryski",
                        "snowboarding",
                    ),
            )
            .flatMap { (sport, names) -> names.map { it to sport } }
            .toMap()

    private val SUB_SPORTS =
        mapOf("indoorcycling" to Sport.RIDE, "virtualactivity" to Sport.RIDE, "treadmill" to Sport.RUN, "trail" to Sport.RUN)

    /**
     * "garmin" + "fenix7" → "Garmin fenix7"; FIT names are snake_case in Java, camelCase in JS. A display name with spaces (TCX's
     * "Forerunner 965") is kept as written.
     */
    private fun device(manufacturer: String?, product: String?): String? =
        listOfNotNull(
                manufacturer?.takeUnless { it.fitKey() in setOf("unknown", "invalid") }?.humanize()?.replaceFirstChar { it.uppercase() },
                product?.humanize(),
            )
            .joinToString(" ")
            .ifBlank { null }

    private fun List<DecodedSample>.toSamples(): List<LocationSample> = mapNotNull { r ->
        val lat = r.latitude ?: return@mapNotNull null
        val lon = r.longitude ?: return@mapNotNull null
        LocationSample(lat, lon, r.altitudeMeters, r.speedMps, accuracy = null, time = Instant.fromEpochMilliseconds(r.timestampMillis!!))
    }
}

/** Same as the recorder's: altitude only counts as climb once it moves more than this, so sensor noise doesn't add up. */
private const val ELEVATION_HYSTERESIS_M = 3.0

/** Same as the recorder's: slower than this between two samples counts as stopped. */
private const val MOVING_SPEED_MPS = 0.5

/** Time spent moving, for files with no summary (GPX). Null below two samples. */
private fun List<LocationSample>.movingSeconds(): Double? = takeIf {
    size >= 2
}
    ?.zipWithNext { a, b ->
        val seconds = (b.time - a.time).inWholeMilliseconds / MILLIS_PER_SECOND
        if (seconds > 0 && TrackMath.distanceMeters(a, b) / seconds >= MOVING_SPEED_MPS) seconds else 0.0
    }
    ?.sum()

private const val MILLIS_PER_SECOND = 1000.0

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
private inline fun List<DecodedSummary>.total(value: (DecodedSummary) -> Double?): Double? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

/** Average across sessions, weighted by timer time (falling back to equal weights). */
private inline fun List<DecodedSummary>.weightedAverage(value: (DecodedSummary) -> Double?): Double? {
    val pairs = mapNotNull { s -> value(s)?.let { it to (s.totalTimerSeconds ?: 1.0) } }
    val weight = pairs.sumOf { it.second }
    return if (pairs.isEmpty() || weight <= 0) null else pairs.sumOf { it.first * it.second } / weight
}

private inline fun List<DecodedSample>.averageOf(value: (DecodedSample) -> Int?): Int? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private inline fun <T : Any> List<DecodedSample>.lastNotNullOf(value: (DecodedSample) -> T?): T? = asReversed().firstNotNullOfOrNull(value)

private fun String.fitKey() = filter { it.isLetterOrDigit() }.lowercase()

private fun String.humanize() =
    if (' ' in trim()) trim()
    else replace('_', ' ').replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().takeUnless { it.isBlank() || it == "unknown" }
