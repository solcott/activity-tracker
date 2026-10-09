package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import kotlin.math.roundToLong

/** A dive's summary in the lexicon's integer units. */
object DiveSummary {
    private const val CM_PER_M = 100
    private const val TENTHS_PER_DEGREE = 10
    private const val SECONDS_PER_MINUTE = 60L
    private const val MBAR_PER_BAR = 1000
    private const val ML_PER_L = 1000

    fun of(dive: ImportedDive): ActivityDive =
        ActivityDive(
            maxDepth = dive.maxDepthMeters?.let { (it * CM_PER_M).count() },
            avgDepth = dive.avgDepthMeters?.let { (it * CM_PER_M).count() },
            minTemperature = dive.minWaterTempCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() },
            bottomTime = dive.bottomTimeSeconds?.count(),
            surfaceInterval = dive.surfaceIntervalMinutes?.let { it.coerceAtLeast(0) * SECONDS_PER_MINUTE },
            diveNumber = dive.diveNumber,
            closedCircuit = dive.closedCircuit.takeIf { it },
            gases =
                dive.gases
                    .take(ActivityDive.MAX_GASES)
                    .map {
                        ActivityDiveGas(
                            it.oxygenPercent.coerceIn(0, ActivityDiveGas.MAX_PERCENT),
                            it.heliumPercent.coerceIn(0, ActivityDiveGas.MAX_PERCENT),
                        )
                    }
                    .ifEmpty { null },
            gradientFactorLow = dive.gradientFactorLow,
            gradientFactorHigh = dive.gradientFactorHigh,
            startCns = dive.startCns,
            endCns = dive.endCns,
            otu = dive.oxygenToxicityOtu,
            sac = dive.sacBarPerMinute?.let { (it * MBAR_PER_BAR).count() },
            rmv = dive.rmvLitersPerMinute?.let { (it * ML_PER_L).count() },
            tanks =
                dive.tanks
                    .take(ActivityDive.MAX_TANKS)
                    .map { tank ->
                        ActivityDiveTank(
                            startPressure = tank.startPressureBar?.let { (it * MBAR_PER_BAR).count() },
                            endPressure = tank.endPressureBar?.let { (it * MBAR_PER_BAR).count() },
                            volumeUsed = tank.volumeUsedLiters?.count(),
                        )
                    }
                    .ifEmpty { null },
        )

    /** A reading the lexicon holds to zero or more: a depth just above the surface (-0.05 m) is noise, not a rejected record. */
    private fun Double.count(): Long = roundToLong().coerceAtLeast(0)

    /** [existing] with each field it lacks taken from [other]. */
    fun fillGaps(existing: ActivityDive?, other: ActivityDive?): ActivityDive? {
        if (existing == null || other == null) return existing ?: other
        return fillGasGaps(fillDepthGaps(existing, other), other)
    }

    private fun fillDepthGaps(existing: ActivityDive, other: ActivityDive): ActivityDive =
        existing.copy(
            maxDepth = existing.maxDepth ?: other.maxDepth,
            avgDepth = existing.avgDepth ?: other.avgDepth,
            minTemperature = existing.minTemperature ?: other.minTemperature,
            bottomTime = existing.bottomTime ?: other.bottomTime,
            surfaceInterval = existing.surfaceInterval ?: other.surfaceInterval,
            diveNumber = existing.diveNumber ?: other.diveNumber,
        )

    private fun fillGasGaps(existing: ActivityDive, other: ActivityDive): ActivityDive =
        existing.copy(
            closedCircuit = existing.closedCircuit ?: other.closedCircuit,
            gases = existing.gases ?: other.gases,
            gradientFactorLow = existing.gradientFactorLow ?: other.gradientFactorLow,
            gradientFactorHigh = existing.gradientFactorHigh ?: other.gradientFactorHigh,
            startCns = existing.startCns ?: other.startCns,
            endCns = existing.endCns ?: other.endCns,
            otu = existing.otu ?: other.otu,
            sac = existing.sac ?: other.sac,
            rmv = existing.rmv ?: other.rmv,
            tanks = existing.tanks ?: other.tanks,
        )
}
