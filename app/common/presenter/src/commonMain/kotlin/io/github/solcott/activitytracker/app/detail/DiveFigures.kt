package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import kotlinx.collections.immutable.toImmutableList

/**
 * The dive computer's summary for the stats list's Dive group: how long, what was breathed, the loading, the rates, the water, the
 * settings.
 */
internal object DiveFigures {
    fun of(dive: ActivityDive): List<Figure?> = times(dive) + breathing(dive) + conditions(dive) + settings(dive)

    private fun times(dive: ActivityDive): List<Figure?> =
        listOf(
            dive.bottomTime?.let { Figure(FigureKey.BottomTime, FigureValue.Duration(it)) },
            dive.surfaceInterval?.let { Figure(FigureKey.SurfaceInterval, minutes(it)) },
            dive.diveNumber?.let { Figure(FigureKey.DiveNumber, FigureValue.Count(it.toLong())) },
        )

    /** The gases, oxygen and nitrogen loading, and how much was used. */
    private fun breathing(dive: ActivityDive): List<Figure?> {
        val tank = dive.tanks?.firstOrNull()
        val start = tank?.startPressure
        val end = tank?.endPressure
        return listOf(
            dive.gases
                ?.takeIf { it.isNotEmpty() }
                ?.let { gases ->
                    val mixes = gases.map { GasMix(it.oxygen, it.helium) }.toImmutableList()
                    Figure(FigureKey.Gas, FigureValue.Gases(mixes, dive.closedCircuit == true))
                },
            dive.startCns?.let { s -> dive.endCns?.let { Figure(FigureKey.Cns, FigureValue.PercentRange(s, it)) } },
            dive.otu?.let { Figure(FigureKey.Otu, FigureValue.Count(it.toLong())) },
            dive.rmv?.let { Figure(FigureKey.Rmv, FigureValue.GasRate(it / ML_PER_L)) }
                ?: dive.sac?.let { Figure(FigureKey.Sac, FigureValue.PressureRate(it / MBAR_PER_BAR)) },
            if (start != null && end != null) {
                Figure(FigureKey.AirUsed, FigureValue.PressureRange(start / MBAR_PER_BAR, end / MBAR_PER_BAR))
            } else {
                null
            },
            dive.endN2?.let { Figure(FigureKey.EndN2, FigureValue.PercentRange(dive.startN2 ?: 0, it)) },
            dive.highestPpO2?.let { Figure(FigureKey.HighestPpO2, FigureValue.Bar(it / MBAR_PER_BAR)) },
        )
    }

    /** The rates, the water and the surface. */
    private fun conditions(dive: ActivityDive): List<Figure?> =
        listOf(
            dive.maxAscentRate?.let { Figure(FigureKey.MaxAscentRate, FigureValue.VerticalRate(it)) },
            dive.descentTime?.let { Figure(FigureKey.DescentTime, FigureValue.Duration(it)) },
            dive.waterType?.let { Figure(FigureKey.WaterType, FigureValue.Token(it)) },
            dive.visibility?.let { Figure(FigureKey.Visibility, FigureValue.Length(it / CM_PER_M)) },
            dive.airTemperature?.let { Figure(FigureKey.AirTemperature, FigureValue.Temperature(it)) },
            dive.altitude?.let { Figure(FigureKey.Altitude, FigureValue.Elevation(it.toDouble())) },
            dive.noFlyTime?.let { Figure(FigureKey.NoFlyTime, minutes(it)) },
            dive.desaturationTime?.let { Figure(FigureKey.DesaturationTime, minutes(it)) },
        )

    /** How the dive computer was set: its deco model, ppO₂ limits, setpoints and safety stop. */
    private fun settings(dive: ActivityDive): List<Figure?> {
        val gradientFactors = dive.gradientFactorLow != null && dive.gradientFactorHigh != null
        val limits = listOfNotNull(dive.ppO2Warn, dive.ppO2Critical, dive.ppO2Deco).isNotEmpty()
        val setpoints = listOfNotNull(dive.lowSetpoint, dive.highSetpoint).isNotEmpty()
        return listOf(
            Figure(FigureKey.DecoModel, FigureValue.DecoModel(dive.decoModel, dive.gradientFactorLow, dive.gradientFactorHigh)).takeIf {
                dive.decoModel != null || gradientFactors
            },
            Figure(FigureKey.PpO2Limits, FigureValue.PpO2Limits(dive.ppO2Warn, dive.ppO2Critical, dive.ppO2Deco)).takeIf { limits },
            Figure(FigureKey.Setpoints, FigureValue.Setpoints(dive.lowSetpoint, dive.highSetpoint)).takeIf { setpoints },
            dive.safetyStopTime?.let { Figure(FigureKey.SafetyStop, FigureValue.Duration(it.toLong())) },
        )
    }

    private fun minutes(seconds: Long) = FigureValue.Minutes((seconds / SECONDS_PER_MINUTE).toInt())

    private const val CM_PER_M = 100.0
    private const val ML_PER_L = 1000.0
    private const val MBAR_PER_BAR = 1000.0
    private const val SECONDS_PER_MINUTE = 60
}
