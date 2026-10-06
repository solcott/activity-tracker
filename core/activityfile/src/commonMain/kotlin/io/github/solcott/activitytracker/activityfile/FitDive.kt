package io.github.solcott.activitytracker.activityfile

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
    /** The `data` of each `dive_gas_switched` event, in time order: the index of the gas switched to. */
    val gasSwitches: List<Int>,
    val tanks: List<FitTankSummary>,
    /** The first session's start and end positions: where the diver went in and came out, if the watch had a fix. */
    val entry: DivePosition?,
    val exit: DivePosition?,
    /** The first session's own dive fields, for a file with no `dive_summary`. */
    val sessionMaxDepthMeters: Double?,
    val sessionAvgDepthMeters: Double?,
    val sessionDiveNumber: Int?,
)

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
)

/**
 * A `dive_gas` slot. Percents are whole numbers; status is enabled, disabled or backup_only, and mode open_circuit or
 * closed_circuit_diluent.
 */
data class FitDiveGas(val index: Int?, val oxygenPercent: Int?, val heliumPercent: Int?, val status: String?, val mode: String?)

/** A `tank_summary`, one per paired pressure transmitter. */
data class FitTankSummary(val startPressureBar: Double?, val endPressureBar: Double?, val volumeUsedLiters: Double?)

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
            gases = gases.map { DiveGas(it.oxygenPercent!!, it.heliumPercent ?: 0) }.distinct(),
            closedCircuit = closedCircuit(subSport, gases),
            avgDepthMeters = summary?.avgDepthMeters ?: messages.sessionAvgDepthMeters,
            bottomTimeSeconds = summary?.bottomTimeSeconds,
            oxygenToxicityOtu = summary?.oxygenToxicityOtu,
            rmvLitersPerMinute = (summary?.avgRmv ?: summary?.avgVolumeSac)?.takeIf { it > 0 },
            sacBarPerMinute = summary?.avgPressureSac?.takeIf { it > 0 },
            tanks = tanks(messages.tanks),
            entry = messages.entry,
            exit = messages.exit,
        )
    }

    /**
     * The dive starts on the first enabled gas (Garmin's gas 1) and each `dive_gas_switched` names the next. Unused slots are disabled
     * placeholders, often reading 21/0, so they don't count.
     */
    private fun gasesBreathed(messages: FitDiveMessages): List<FitDiveGas> {
        val usable = messages.gases.filter { (it.oxygenPercent ?: 0) > 0 && it.status?.fitKey() !in UNUSABLE }.sortedBy { it.index }
        val first = usable.firstOrNull { it.status?.fitKey() != "backuponly" } ?: usable.firstOrNull()
        val switchedTo = messages.gasSwitches.mapNotNull { index -> usable.firstOrNull { it.index == index } }
        return (listOfNotNull(first) + switchedTo).distinctBy { it.index }
    }

    private fun FitDiveMessages.hasDiveMessages() = summaries.isNotEmpty() || gases.isNotEmpty() || tanks.isNotEmpty()

    private fun closedCircuit(subSport: String?, gases: List<FitDiveGas>) =
        subSport?.fitKey() == "ccrdiving" || gases.any { it.mode?.fitKey() == "closedcircuitdiluent" }

    /** A paired transmitter that wasn't on a tank reads 0 throughout. */
    private fun tanks(tanks: List<FitTankSummary>) =
        tanks
            .filter { (it.startPressureBar ?: 0.0) > 0 || (it.endPressureBar ?: 0.0) > 0 }
            .map { DiveTank(it.startPressureBar, it.endPressureBar, it.volumeUsedLiters?.takeIf { v -> v > 0 }) }

    /** MesgNum 18; an SDK that doesn't name it passes the number. */
    private val SESSION = setOf("session", "18")
    private val UNUSABLE = setOf("disabled", "invalid")
    private const val SECONDS_PER_MINUTE = 60
}
