package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/** Builds an activity's [ActivityStreamData] from a file's samples or a recording, in the lexicon's integer units. */
object StreamBuilder {
    /**
     * [records] are sorted and all have a timestamp. A dive's [gases] (in order of first use) start its [gasSwitches] at time 0, and its
     * [tanks]' [tankUpdates] become a pressure series each, in the tanks' order. [laps] are kept unless there's only one, spanning the
     * [elapsedSeconds]. Null when no sample has anything to chart.
     */
    @Suppress("LongParameterList") // Everything a file holds over time.
    fun fromRecords(
        records: List<DecodedSample>,
        startedAtMillis: Long,
        gearChanges: List<DecodedGearChange>,
        gasSwitches: List<DecodedGasSwitch> = emptyList(),
        gases: List<DiveGas> = emptyList(),
        tanks: List<DiveTank> = emptyList(),
        tankUpdates: List<DecodedTankUpdate> = emptyList(),
        laps: List<DecodedSummary> = emptyList(),
        elapsedSeconds: Double? = null,
    ): ActivityStreamData? {
        if (records.isEmpty()) return null
        fun seconds(millis: Long) = ((millis - startedAtMillis) / MILLIS_PER_SECOND).roundToInt()
        val time = records.map { seconds(requireNotNull(it.timestampMillis)) }
        val distance = records.channel { it.distanceMeters?.times(CENTI) } ?: positionDistance(records)
        // The next stop's depth and time are 0 outside deco; the lexicon leaves them out there.
        fun stop(r: DecodedSample) = r.nextStopDepthMeters?.takeIf { it > 0 }
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
                respirationRate = records.channel { it.respirationRate?.times(DECI) },
                verticalOscillation = records.channel { it.verticalOscillationMm },
                stanceTime = records.channel { it.stanceTimeMs },
                stepLength = records.channel { it.stepLengthMm },
                verticalRatio = records.channel { it.verticalRatioPercent?.times(CENTI) },
                stanceTimeBalance = records.channel { it.stanceTimeBalancePercent?.times(CENTI) },
                leftRightBalance = records.channel { r -> r.leftRightBalance?.let(FitBalance::rightPercent)?.toDouble() },
                // A rebreather's cells measure what's breathed; the average is what dive software shows.
                ppO2 = records.channel { r -> (r.measuredPpO2Bars.takeIf { it.isNotEmpty() }?.average() ?: r.ppO2Bar)?.times(MILLI) },
                cns = records.channel { it.cnsPercent?.toDouble() },
                n2Load = records.channel { it.n2LoadPercent?.toDouble() },
                ndl = records.channel { it.ndlSeconds?.toDouble() },
                decoStopDepth = records.channel { r -> stop(r)?.times(CENTI) },
                decoStopTime = records.channel { r -> r.nextStopSeconds?.takeIf { stop(r) != null }?.toDouble() },
                tts = records.channel { it.ttsSeconds?.toDouble() },
                gasSwitches = gasSwitches(gasSwitches.map { seconds(it.timestampMillis) to it.gas }, gases.firstOrNull()),
                tankPressures =
                    tankPressures(time, tanks, tankUpdates.map { Triple(seconds(it.timestampMillis), it.sensor, it.pressureBar) }),
                laps = laps(laps, ::seconds, elapsedSeconds),
            )
        return data.takeIf { it.channels.isNotEmpty() }
    }

    /**
     * Each switch in time order, starting with the first gas at 0: a dive that never switched explicitly to it still breathed it first. A
     * switch to the gas already breathed changes nothing and goes.
     */
    private fun gasSwitches(switches: List<Pair<Int, DiveGas>>, firstGas: DiveGas?): List<GasSwitch>? {
        val sorted = switches.map { (t, gas) -> t.coerceAtLeast(0) to gas }.sortedBy { it.first }
        val first = firstGas ?: sorted.firstOrNull()?.second ?: return null
        val starting =
            when {
                sorted.firstOrNull()?.first == 0 -> sorted
                // A file that switches to the first gas once under way: it was breathed from the start.
                sorted.firstOrNull()?.second == first -> listOf(0 to first) + sorted.drop(1)
                else -> listOf(0 to first) + sorted
            }
        var breathing: DiveGas? = null
        return starting.mapNotNull { (t, gas) ->
            if (gas == breathing) return@mapNotNull null
            breathing = gas
            GasSwitch(t, gas.oxygenPercent, gas.heliumPercent)
        }
    }

    /**
     * One series per tank in [tanks]' order, each reading on the nearest sample within [MAX_GAP_SECONDS] (the nearest reading wins a sample
     * two share). Null when no tank has a reading.
     */
    private fun tankPressures(time: List<Int>, tanks: List<DiveTank>, updates: List<Triple<Int, String, Double>>): List<TankPressure>? {
        if (updates.isEmpty() || tanks.isEmpty()) return null
        val series = tanks.map { tank ->
            val pressure = arrayOfNulls<Int>(time.size)
            val gap = IntArray(time.size) { Int.MAX_VALUE }
            for ((t, _, bar) in updates.filter { it.second == tank.sensor }) {
                val index = nearest(time, t) ?: continue
                val off = abs(time[index] - t)
                if (off < gap[index]) {
                    gap[index] = off
                    pressure[index] = (bar * MILLI).roundToInt()
                }
            }
            TankPressure(pressure.toList())
        }
        return series.takeIf { tanksRead -> tanksRead.any { tank -> tank.pressure.any { it != null } } }
    }

    /** The index in sorted [times] closest to [t], if within [MAX_GAP_SECONDS]. */
    private fun nearest(times: List<Int>, t: Int): Int? {
        val found = times.binarySearch(t)
        val index =
            if (found >= 0) {
                found
            } else {
                val after = -found - 1
                listOf(after - 1, after).filter { it in times.indices }.minByOrNull { abs(times[it] - t) } ?: return null
            }
        return index.takeIf { abs(times[it] - t) <= MAX_GAP_SECONDS }
    }

    /** The laps in order, or null for one lap spanning the activity (within a second), which says nothing the summary doesn't. */
    private fun laps(laps: List<DecodedSummary>, seconds: (Long) -> Int, elapsedSeconds: Double?): List<Lap>? {
        val kept =
            laps
                .mapNotNull { lap ->
                    val start = lap.startTimeMillis?.let(seconds) ?: return@mapNotNull null
                    val elapsed = lap.totalElapsedSeconds?.roundToInt() ?: return@mapNotNull null
                    Lap(
                        start = start,
                        elapsed = elapsed,
                        moving = lap.totalTimerSeconds?.roundToInt(),
                        distance = lap.totalDistanceMeters?.roundToInt(),
                        avgSpeed = lap.avgSpeedMps?.times(MILLI)?.roundToInt(),
                        maxSpeed = lap.maxSpeedMps?.times(MILLI)?.roundToInt(),
                        avgHeartRate = lap.avgHeartRate,
                        maxHeartRate = lap.maxHeartRate,
                        avgPower = lap.avgPowerWatts,
                        avgCadence = lap.avgCadence,
                        ascent = lap.totalAscentMeters?.roundToInt(),
                        descent = lap.totalDescentMeters?.roundToInt(),
                    )
                }
                .sortedBy { it.start }
        val only = kept.singleOrNull()
        val spans = only != null && abs(only.start) <= 1 && (elapsedSeconds == null || abs(only.elapsed - elapsedSeconds) <= 1)
        return kept.takeIf { it.isNotEmpty() && !spans }
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

    /** Dive computers sample every 1 to 10 seconds; a reading further than this from every sample has no place. */
    const val MAX_GAP_SECONDS = 30
}
