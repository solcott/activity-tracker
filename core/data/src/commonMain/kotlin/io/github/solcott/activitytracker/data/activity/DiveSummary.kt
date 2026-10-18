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

    /** [existing] with each field it lacks taken from [other]. */
    fun fillGaps(existing: ActivityDive?, other: ActivityDive?): ActivityDive? {
        if (existing == null || other == null) return existing ?: other
        return fillSettingGaps(fillConditionGaps(fillGasGaps(fillDepthGaps(existing, other), other), other), other)
    }

    private fun fillDepthGaps(existing: ActivityDive, other: ActivityDive): ActivityDive =
        existing.copy(
            maxDepth = existing.maxDepth ?: other.maxDepth,
            avgDepth = existing.avgDepth ?: other.avgDepth,
            minTemperature = existing.minTemperature ?: other.minTemperature,
            bottomTime = existing.bottomTime ?: other.bottomTime,
            surfaceInterval = existing.surfaceInterval ?: other.surfaceInterval,
            diveNumber = existing.diveNumber ?: other.diveNumber,
            site = existing.site ?: other.site,
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
            startN2 = existing.startN2 ?: other.startN2,
            endN2 = existing.endN2 ?: other.endN2,
            highestPpO2 = existing.highestPpO2 ?: other.highestPpO2,
        )

    private fun fillConditionGaps(existing: ActivityDive, other: ActivityDive): ActivityDive =
        existing.copy(
            avgAscentRate = existing.avgAscentRate ?: other.avgAscentRate,
            maxAscentRate = existing.maxAscentRate ?: other.maxAscentRate,
            avgDescentRate = existing.avgDescentRate ?: other.avgDescentRate,
            maxDescentRate = existing.maxDescentRate ?: other.maxDescentRate,
            descentTime = existing.descentTime ?: other.descentTime,
            waterType = existing.waterType ?: other.waterType,
            waterDensity = existing.waterDensity ?: other.waterDensity,
            airTemperature = existing.airTemperature ?: other.airTemperature,
            visibility = existing.visibility ?: other.visibility,
            altitude = existing.altitude ?: other.altitude,
            noFlyTime = existing.noFlyTime ?: other.noFlyTime,
            desaturationTime = existing.desaturationTime ?: other.desaturationTime,
        )

    private fun fillSettingGaps(existing: ActivityDive, other: ActivityDive): ActivityDive =
        existing.copy(
            decoModel = existing.decoModel ?: other.decoModel,
            ppO2Warn = existing.ppO2Warn ?: other.ppO2Warn,
            ppO2Critical = existing.ppO2Critical ?: other.ppO2Critical,
            ppO2Deco = existing.ppO2Deco ?: other.ppO2Deco,
            lowSetpoint = existing.lowSetpoint ?: other.lowSetpoint,
            highSetpoint = existing.highSetpoint ?: other.highSetpoint,
            safetyStopTime = existing.safetyStopTime ?: other.safetyStopTime,
        )
}
