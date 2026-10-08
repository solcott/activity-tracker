package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.roundToInt
import kotlin.time.Instant

/** Builds an activity's [ActivityStreamData] from a file's samples or a recording, in the lexicon's integer units. */
object StreamBuilder {
    /** [records] are sorted and all have a timestamp. Null when no sample has anything to chart. */
    fun fromRecords(records: List<DecodedSample>, startedAtMillis: Long, gearChanges: List<DecodedGearChange>): ActivityStreamData? {
        if (records.isEmpty()) return null
        val time = records.map { ((requireNotNull(it.timestampMillis) - startedAtMillis) / MILLIS_PER_SECOND).roundToInt() }
        val distance = records.channel { it.distanceMeters?.times(CENTI) } ?: positionDistance(records)
        val data =
            ActivityStreamData(
                time = time,
                distance = distance,
                altitude = records.channel { it.altitudeMeters?.times(CENTI) },
                speed = records.channel { it.speedMps?.times(MILLI) } ?: distance?.let { speedFrom(time, it) },
                heartRate = records.channel { it.heartRate?.toDouble() },
                power = records.channel { it.powerWatts?.toDouble() },
                cadence = records.channel { it.cadence?.toDouble() },
                temperature = records.channel { it.temperatureCelsius?.times(DECI) },
                depth = records.channel { it.depthMeters?.times(CENTI) },
                gears = gears(gearChanges, startedAtMillis),
            )
        return data.takeIf { it.channels.isNotEmpty() }
    }

    /** A phone recording that started at [startedAt]: distance, altitude and speed only. Null for fewer than two samples. */
    fun fromLocations(samples: List<LocationSample>, startedAt: Instant): ActivityStreamData? {
        if (samples.size < 2) return null
        val start = startedAt
        val time = samples.map { (it.time - start).inWholeSeconds.toInt() }
        var total = 0.0
        val distance = samples.mapIndexed { i, s ->
            if (i > 0) total += TrackMath.distanceMeters(samples[i - 1], s)
            (total * CENTI).roundToInt()
        }
        return ActivityStreamData(
            time = time,
            distance = distance,
            altitude = samples.map { it.altitude?.times(CENTI)?.roundToInt() }.takeIf { v -> v.any { it != null } },
            speed = samples.map { it.speed?.times(MILLI)?.roundToInt() }.takeIf { v -> v.any { it != null } } ?: speedFrom(time, distance),
        )
    }

    private fun List<DecodedSample>.channel(value: (DecodedSample) -> Double?): List<Int?>? = map {
        value(it)?.roundToInt()
    }
        .takeIf { values -> values.any { it != null } }

    /** Cumulative distance along the samples that have a position, for files without a distance channel (most GPX). */
    private fun positionDistance(records: List<DecodedSample>): List<Int?>? {
        var total = 0.0
        var last: LocationSample? = null
        val distance = records.map { r ->
            val lat = r.latitude ?: return@map null
            val lon = r.longitude ?: return@map null
            val here = LocationSample(lat, lon, null, null, null, Instant.fromEpochMilliseconds(0))
            last?.let { total += TrackMath.distanceMeters(it, here) }
            last = here
            (total * CENTI).roundToInt()
        }
        return distance.takeIf { values -> values.count { it != null } >= 2 }
    }

    /** Speed in mm/s from distance (cm), over a few samples either side to steady GPS jitter. */
    private fun speedFrom(time: List<Int>, distance: List<Int?>): List<Int?> =
        time.indices.map { i ->
            val from = (i - SPEED_SPAN).coerceAtLeast(0)
            val to = (i + SPEED_SPAN).coerceAtMost(time.lastIndex)
            val a = distance[from]
            val b = distance[to]
            val seconds = time[to] - time[from]
            if (a == null || b == null || seconds <= 0) null else ((b - a) * (MILLI / CENTI) / seconds).roundToInt()
        }

    private fun gears(changes: List<DecodedGearChange>, startedAtMillis: Long): List<GearChange>? =
        changes
            .mapNotNull { change ->
                val at = change.timestampMillis ?: return@mapNotNull null
                if (change.frontGear == null && change.rearGear == null) return@mapNotNull null
                GearChange(((at - startedAtMillis) / MILLIS_PER_SECOND).roundToInt(), change.frontGear, change.rearGear)
            }
            .sortedBy { it.time }
            .takeIf { it.isNotEmpty() }

    private const val MILLIS_PER_SECOND = 1000.0
    private const val DECI = 10.0
    private const val CENTI = 100.0
    private const val MILLI = 1000.0
    private const val SPEED_SPAN = 2
}
