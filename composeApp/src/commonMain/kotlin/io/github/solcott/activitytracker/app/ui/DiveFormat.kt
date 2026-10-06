package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.unit_gas_rate_cuft
import io.github.solcott.activitytracker.composeApp.resources.unit_gas_rate_l
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_bar
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_psi
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_rate_bar
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_rate_psi
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.DiveConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

/** Display formatting for a dive's tank pressures and air consumption, following [LocalUnitSystem] like [Format]. */
object DiveFormat {
    private const val RATE_DECIMALS = 1
    private const val CUBIC_FEET_DECIMALS = 2

    /** A tank pressure in whole bar, or psi. */
    @Composable
    fun pressure(bar: Double?): String {
        if (bar == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = DiveConversions.toPressureUnit(bar, units).roundToLong()
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_pressure_bar, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_pressure_psi, value)
        }
    }

    /** Air consumption by tank pressure: bar per minute to one decimal, or whole psi per minute. */
    @Composable
    fun pressureRate(barPerMinute: Double): String {
        val units = LocalUnitSystem.current
        val value = DiveConversions.toPressureUnit(barPerMinute, units)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_pressure_rate_bar, formatDecimal(value, RATE_DECIMALS))
            UnitSystem.Imperial -> stringResource(Res.string.unit_pressure_rate_psi, value.roundToLong())
        }
    }

    /** Air consumption by volume: liters per minute to one decimal, or cubic feet per minute to two. */
    @Composable
    fun gasRate(litersPerMinute: Double): String {
        val units = LocalUnitSystem.current
        val value = DiveConversions.toGasVolumeUnit(litersPerMinute, units)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_gas_rate_l, formatDecimal(value, RATE_DECIMALS))
            UnitSystem.Imperial -> stringResource(Res.string.unit_gas_rate_cuft, formatDecimal(value, CUBIC_FEET_DECIMALS))
        }
    }
}
