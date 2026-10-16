package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.detail.GasKind
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_air
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_nitrox
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_oxygen
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_trimix
import io.github.solcott.activitytracker.composeApp.resources.unit_days_hours
import io.github.solcott.activitytracker.composeApp.resources.unit_gas_rate_cuft
import io.github.solcott.activitytracker.composeApp.resources.unit_gas_rate_l
import io.github.solcott.activitytracker.composeApp.resources.unit_hours_minutes
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

/**
 * Display formatting for a dive's gases, surface interval, tank pressures and air consumption, following [LocalUnitSystem] like [Format].
 */
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

    /** "Air", "EAN32", "Oxygen" or "Tx 18/45", as [GasKind] names it. */
    @Composable
    fun gasName(oxygenPercent: Int, heliumPercent: Int): String =
        when (GasKind.of(oxygenPercent, heliumPercent)) {
            GasKind.Trimix -> stringResource(Res.string.dive_gas_trimix, oxygenPercent, heliumPercent)
            GasKind.Air -> stringResource(Res.string.dive_gas_air)
            GasKind.Oxygen -> stringResource(Res.string.dive_gas_oxygen)
            GasKind.Nitrox -> stringResource(Res.string.dive_gas_nitrox, oxygenPercent)
        }

    /**
     * "5 h 55 min" within a day, as a clock time reads badly; "11 d 22 h" beyond, where minutes are noise. FIT logs count the interval
     * since the last dive however long ago it was.
     */
    @Composable
    fun surfaceInterval(minutes: Int): String {
        val hours = minutes / MINUTES_PER_HOUR
        return if (hours < HOURS_PER_DAY) stringResource(Res.string.unit_hours_minutes, hours, minutes % MINUTES_PER_HOUR)
        else stringResource(Res.string.unit_days_hours, hours / HOURS_PER_DAY, hours % HOURS_PER_DAY)
    }

    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
}
