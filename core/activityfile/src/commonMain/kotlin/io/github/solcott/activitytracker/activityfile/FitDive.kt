package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.WaterType

/**
 * A FIT dive log's dive messages, as each platform's SDK decodes them (SI units, enum values as the SDK names them). The decoders only
 * translate; [FitDives] interprets, so it's the same everywhere.
 */
data class FitDiveMessages(
    val summaries: List<FitDiveSummary>,
    /** From the first `dive_settings`. */
    val gradientFactorLow: Int?,
    val gradientFactorHigh: Int?,
    /** The gas slots the computer was set up with, used or not. */
    val gases: List<FitDiveGas>,
    /** Each `dive_gas_switched` event, in time order. */
    val gasSwitches: List<FitGasSwitch>,
    val tanks: List<FitTankSummary>,
    /** The first session's start and end positions: where the diver went in and came out, if the watch had a fix. */
    val entry: DivePosition?,
    val exit: DivePosition?,
    /** The first session's own dive fields, for a file with no `dive_summary`. */
    val sessionMaxDepthMeters: Double?,
    val sessionAvgDepthMeters: Double?,
    val sessionDiveNumber: Int?,
    /** From the first `dive_settings`: "fresh", "salt", "en13319" or "custom" in the SDK's spelling. */
    val waterType: String? = null,
    val waterDensityKgPerM3: Double? = null,
    /** Each `tank_update`, in file order. */
    val tankUpdates: List<FitTankUpdate> = emptyList(),
)

/** A `dive_gas_switched` event: [gasIndex] is its `data`, the `dive_gas` message index of the gas switched to. */
data class FitGasSwitch(val timestampMillis: Long?, val gasIndex: Int)

/** A `tank_update`: one transmitter's reading. */
data class FitTankUpdate(val timestampMillis: Long?, val sensor: Long?, val pressureBar: Double?)

/** A `dive_summary`: one for the session, and usually another per lap. */
data class FitDiveSummary(
    /** The message it summarizes: "session" or "lap" in the SDK's spelling, or a message number the SDK doesn't name. */
    val referenceMesg: String?,
    val avgDepthMeters: Double?,
    val maxDepthMeters: Double?,
    val surfaceIntervalSeconds: Double?,
    val diveNumber: Int?,
    val bottomTimeSeconds: Double?,
    val startCns: Int?,
    val endCns: Int?,
    val oxygenToxicityOtu: Int?,
    /** Respiratory minute volume, in liters per minute; needs a tank size. */
    val avgRmv: Double?,
    /** Surface air consumption by volume, in liters per minute; needs a tank size. */
    val avgVolumeSac: Double?,
    /** Surface air consumption by tank pressure, in bar per minute. */
    val avgPressureSac: Double?,
    /** Nitrogen loading, in percent of the limit. */
    val startN2: Int? = null,
    val endN2: Int? = null,
    /** Meters per second. */
    val avgAscentRate: Double? = null,
    val maxAscentRate: Double? = null,
    val avgDescentRate: Double? = null,
    val maxDescentRate: Double? = null,
    val descentTimeSeconds: Double? = null,
)

/**
 * A `dive_gas` slot. Percents are whole numbers; status is enabled, disabled or backup_only, and mode open_circuit or
 * closed_circuit_diluent.
 */
data class FitDiveGas(val index: Int?, val oxygenPercent: Int?, val heliumPercent: Int?, val status: String?, val mode: String?)

/** A `tank_summary`, one per paired pressure transmitter. */
data class FitTankSummary(
    val startPressureBar: Double?,
    val endPressureBar: Double?,
    val volumeUsedLiters: Double?,
    val sensor: Long? = null,
)

/** Interprets [FitDiveMessages] into the [DecodedDive] that Shearwater files fill too. */
object FitDives {
    /** Null when the file has no dive data at all. */
    fun interpret(messages: FitDiveMessages, sport: String?, subSport: String?): DecodedDive? {
        if (sport?.fitKey() != "diving" && !messages.hasDiveMessages()) return null

        // Lap summaries repeat the session's depths but lack its dive number, CNS and OTU.
        val summary = messages.summaries.firstOrNull { it.referenceMesg?.fitKey() in SESSION } ?: messages.summaries.lastOrNull()
        val gases = gasesBreathed(messages)
        return DecodedDive(
            maxDepthMeters = summary?.maxDepthMeters ?: messages.sessionMaxDepthMeters,
            diveNumber = summary?.diveNumber ?: messages.sessionDiveNumber,
            surfaceIntervalMinutes = summary?.surfaceIntervalSeconds?.let { (it / SECONDS_PER_MINUTE).toInt() },
            gradientFactorLow = messages.gradientFactorLow?.takeIf { it > 0 },
            gradientFactorHigh = messages.gradientFactorHigh?.takeIf { it > 0 },
            startCns = summary?.startCns,
            endCns = summary?.endCns,
            gases = gases.mapNotNull { gas -> gas.oxygenPercent?.let { DiveGas(it, gas.heliumPercent ?: 0) } }.distinct(),
            closedCircuit = closedCircuit(subSport, gases),
            avgDepthMeters = summary?.avgDepthMeters ?: messages.sessionAvgDepthMeters,
            bottomTimeSeconds = summary?.bottomTimeSeconds,
            oxygenToxicityOtu = summary?.oxygenToxicityOtu,
            rmvLitersPerMinute = (summary?.avgRmv ?: summary?.avgVolumeSac)?.takeIf { it > 0 },
            sacBarPerMinute = summary?.avgPressureSac?.takeIf { it > 0 },
            tanks = tanks(messages),
            entry = messages.entry,
            exit = messages.exit,
            startN2 = summary?.startN2,
            endN2 = summary?.endN2,
            avgAscentRateMps = summary?.avgAscentRate?.takeIf { it > 0 },
            maxAscentRateMps = summary?.maxAscentRate?.takeIf { it > 0 },
            avgDescentRateMps = summary?.avgDescentRate?.takeIf { it > 0 },
            maxDescentRateMps = summary?.maxDescentRate?.takeIf { it > 0 },
            descentTimeSeconds = summary?.descentTimeSeconds?.takeIf { it > 0 },
            waterType = messages.waterType?.fitKey()?.let(WATER_TYPES::get),
            waterDensityKgPerM3 = messages.waterDensityKgPerM3?.takeIf { it > 0 },
        )
    }

    /**
     * Each `dive_gas_switched` event with a time, as the gas it names. A switch to a slot the file doesn't define (or that's disabled) is
     * dropped; the gases breathed don't depend on this.
     */
    fun gasSwitches(messages: FitDiveMessages): List<DecodedGasSwitch> {
        val usable = usableGases(messages)
        return messages.gasSwitches.mapNotNull { switch ->
            val gas = usable.firstOrNull { it.index == switch.gasIndex }
            val oxygen = gas?.oxygenPercent
            val time = switch.timestampMillis
            if (oxygen == null || time == null) null else DecodedGasSwitch(time, DiveGas(oxygen, gas.heliumPercent ?: 0))
        }
    }

    /** Each `tank_update` with a reading from a sensor [interpret]'s tanks name, in file order. */
    fun tankUpdates(messages: FitDiveMessages): List<DecodedTankUpdate> {
        val sensors = tanks(messages).mapNotNull { it.sensor }.toSet()
        return messages.tankUpdates.mapNotNull { update ->
            val sensor = update.sensor?.toString()?.takeIf { it in sensors }
            val pressure = update.pressureBar?.takeIf { it > 0 }
            val time = update.timestampMillis
            if (sensor == null || pressure == null || time == null) null else DecodedTankUpdate(time, sensor, pressure)
        }
    }

    /**
     * The dive starts on the first enabled gas (Garmin's gas 1) and each `dive_gas_switched` names the next. Unused slots are disabled
     * placeholders, often reading 21/0, so they don't count.
     */
    private fun gasesBreathed(messages: FitDiveMessages): List<FitDiveGas> {
        val usable = usableGases(messages)
        val first = usable.firstOrNull { it.status?.fitKey() != "backuponly" } ?: usable.firstOrNull()
        val switchedTo = messages.gasSwitches.mapNotNull { switch -> usable.firstOrNull { it.index == switch.gasIndex } }
        return (listOfNotNull(first) + switchedTo).distinctBy { it.index }
    }

    private fun usableGases(messages: FitDiveMessages) =
        messages.gases.filter { (it.oxygenPercent ?: 0) > 0 && it.status?.fitKey() !in UNUSABLE }.sortedBy { it.index }

    private fun FitDiveMessages.hasDiveMessages() =
        summaries.isNotEmpty() || gases.isNotEmpty() || tanks.isNotEmpty() || tankUpdates.isNotEmpty()

    private fun closedCircuit(subSport: String?, gases: List<FitDiveGas>) =
        subSport?.fitKey() == "ccrdiving" || gases.any { it.mode?.fitKey() == "closedcircuitdiluent" }

    /**
     * One per `tank_summary`, in their order, so tank pressure series line up with them. A paired transmitter that wasn't on a tank reads 0
     * throughout. A file with updates but no summaries gets a tank per sensor, in the order they first report.
     */
    private fun tanks(messages: FitDiveMessages): List<DiveTank> {
        if (messages.tanks.isNotEmpty()) {
            return messages.tanks
                .filter { (it.startPressureBar ?: 0.0) > 0 || (it.endPressureBar ?: 0.0) > 0 }
                .map {
                    DiveTank(
                        it.startPressureBar,
                        it.endPressureBar,
                        it.volumeUsedLiters?.takeIf { v -> v > 0 },
                        sensor = it.sensor?.toString(),
                    )
                }
        }
        return messages.tankUpdates
            .filter { it.sensor != null && (it.pressureBar ?: 0.0) > 0 }
            .groupBy { it.sensor }
            .map { (sensor, updates) ->
                DiveTank(updates.first().pressureBar, updates.last().pressureBar, null, sensor = sensor.toString())
            }
    }

    /** MesgNum 18; an SDK that doesn't name it passes the number. */
    private val SESSION = setOf("session", "18")
    private val UNUSABLE = setOf("disabled", "invalid")

    /** FIT `water_type`, keyed as [fitKey] spells it. */
    private val WATER_TYPES =
        mapOf("fresh" to WaterType.FRESH, "salt" to WaterType.SALT, "en13319" to WaterType.EN13319, "custom" to WaterType.CUSTOM)
    private const val SECONDS_PER_MINUTE = 60
}
