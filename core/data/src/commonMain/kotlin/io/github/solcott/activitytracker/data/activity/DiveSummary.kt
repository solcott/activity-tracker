package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.DiveGas
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
    private const val MM_PER_M = 1000
    private const val KG_PER_M3_MAX = 2000

    /** The lexicon's `maxLength` for `decoModel`. */
    private const val MAX_TOKEN = 64

    @Suppress("CyclomaticComplexMethod") // A conversion per field.
    fun of(dive: ImportedDive): ActivityDive =
        ActivityDive(
            maxDepth = dive.maxDepthMeters?.let { (it * CM_PER_M).count() },
            avgDepth = dive.avgDepthMeters?.let { (it * CM_PER_M).count() },
            minTemperature = dive.minWaterTempCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() },
            bottomTime = dive.bottomTimeSeconds?.count(),
            surfaceInterval = dive.surfaceIntervalMinutes?.let { it.coerceAtLeast(0) * SECONDS_PER_MINUTE },
            diveNumber = dive.diveNumber,
            closedCircuit = dive.closedCircuit.takeIf { it },
            gases = dive.gases.take(ActivityDive.MAX_GASES).map(::gas).ifEmpty { null },
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
                            volume = tank.volumeLiters?.let { (it * ML_PER_L).count() },
                            gas = tank.gas?.let(::gas),
                        )
                    }
                    .ifEmpty { null },
            site = dive.siteName?.trim()?.take(ActivityDive.MAX_SITE_LENGTH)?.ifEmpty { null },
            startN2 = dive.startN2?.coerceAtLeast(0),
            endN2 = dive.endN2?.coerceAtLeast(0),
            avgAscentRate = dive.avgAscentRateMps?.let { (it * MM_PER_M).count() },
            maxAscentRate = dive.maxAscentRateMps?.let { (it * MM_PER_M).count() },
            avgDescentRate = dive.avgDescentRateMps?.let { (it * MM_PER_M).count() },
            maxDescentRate = dive.maxDescentRateMps?.let { (it * MM_PER_M).count() },
            descentTime = dive.descentTimeSeconds?.count(),
            waterType = dive.waterType,
            waterDensity = dive.waterDensityKgPerM3?.roundToLong()?.toInt()?.takeIf { it in 1..KG_PER_M3_MAX },
            airTemperature = dive.airTemperatureCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() },
            visibility = dive.visibilityMeters?.let { (it * CM_PER_M).count() },
            altitude = dive.altitudeMeters?.roundToLong(),
            noFlyTime = dive.noFlyTimeSeconds?.count(),
            desaturationTime = dive.desaturationTimeSeconds?.count(),
            highestPpO2 = dive.highestPpO2Bar?.let(::millibar),
            decoModel = dive.decoModel?.take(MAX_TOKEN),
            ppO2Warn = dive.ppO2WarnBar?.let(::millibar),
            ppO2Critical = dive.ppO2CriticalBar?.let(::millibar),
            ppO2Deco = dive.ppO2DecoBar?.let(::millibar),
            lowSetpoint = dive.lowSetpointBar?.let(::millibar),
            highSetpoint = dive.highSetpointBar?.let(::millibar),
            safetyStopTime = dive.safetyStopSeconds?.takeIf { it > 0 },
        )

    private fun millibar(bar: Double): Int = (bar * MBAR_PER_BAR).count().toInt()

    private fun gas(gas: DiveGas) =
        ActivityDiveGas(
            gas.oxygenPercent.coerceIn(0, ActivityDiveGas.MAX_PERCENT),
            gas.heliumPercent.coerceIn(0, ActivityDiveGas.MAX_PERCENT),
        )

    /** A reading the lexicon holds to zero or more: a depth just above the surface (-0.05 m) is noise, not a rejected record. */
    private fun Double.count(): Long = roundToLong().coerceAtLeast(0)
}
