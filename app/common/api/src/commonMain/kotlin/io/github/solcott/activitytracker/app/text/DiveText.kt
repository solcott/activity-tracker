package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.app.detail.GasKind
import io.github.solcott.activitytracker.data.units.DiveConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.WaterType
import kotlin.math.roundToLong

/**
 * How a dive's gases, surface interval, tank pressures and air consumption read in the viewer's units (dev/shared-display-text). The shared
 * UI's `DiveFormat` and the native page both resolve these.
 */
object DiveText {
    private const val RATE_DECIMALS = 1
    private const val CUBIC_FEET_DECIMALS = 2
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
    private const val MBAR_PER_BAR = 1000.0
    private const val BAR_DECIMALS = 2

    /** A tank pressure in whole bar, or psi. */
    fun pressure(bar: Double?, units: UnitSystem): AppText {
        if (bar == null) return StatText.missing
        val value = formatWhole(DiveConversions.toPressureUnit(bar, units).roundToLong())
        return AppText.named(if (units == UnitSystem.Metric) "unit_pressure_bar" else "unit_pressure_psi", AppText.written(value))
    }

    /** Air consumption by tank pressure: bar per minute to one decimal, or whole psi per minute. */
    fun pressureRate(barPerMinute: Double, units: UnitSystem): AppText {
        val value = DiveConversions.toPressureUnit(barPerMinute, units)
        return when (units) {
            UnitSystem.Metric -> AppText.named("unit_pressure_rate_bar", AppText.written(formatDecimal(value, RATE_DECIMALS)))
            UnitSystem.Imperial -> AppText.named("unit_pressure_rate_psi", AppText.written(formatWhole(value.roundToLong())))
        }
    }

    /** Air consumption by volume: liters per minute to one decimal, or cubic feet per minute to two. */
    fun gasRate(litersPerMinute: Double, units: UnitSystem): AppText {
        val value = DiveConversions.toGasVolumeUnit(litersPerMinute, units)
        return when (units) {
            UnitSystem.Metric -> AppText.named("unit_gas_rate_l", AppText.written(formatDecimal(value, RATE_DECIMALS)))
            UnitSystem.Imperial -> AppText.named("unit_gas_rate_cuft", AppText.written(formatDecimal(value, CUBIC_FEET_DECIMALS)))
        }
    }

    /** "Air", "EAN32", "Oxygen" or "Tx 18/45", as [GasKind] names it. */
    fun gasName(oxygenPercent: Int, heliumPercent: Int): AppText =
        when (GasKind.of(oxygenPercent, heliumPercent)) {
            GasKind.Trimix -> AppText.named("dive_gas_trimix", AppText.written("$oxygenPercent"), AppText.written("$heliumPercent"))
            GasKind.Air -> AppText.named("dive_gas_air")
            GasKind.Oxygen -> AppText.named("dive_gas_oxygen")
            GasKind.Nitrox -> AppText.named("dive_gas_nitrox", AppText.written("$oxygenPercent"))
        }

    /**
     * "5 h 55 min" within a day, as a clock time reads badly; "11 d 22 h" beyond, where minutes are noise. FIT logs count the interval
     * since the last dive however long ago it was.
     */
    fun surfaceInterval(minutes: Int): AppText {
        val hours = minutes / MINUTES_PER_HOUR
        return if (hours < HOURS_PER_DAY) {
            AppText.named("unit_hours_minutes", AppText.written("$hours"), AppText.written("${minutes % MINUTES_PER_HOUR}"))
        } else {
            AppText.named(
                "unit_days_hours",
                AppText.written(formatWhole(hours / HOURS_PER_DAY)),
                AppText.written("${hours % HOURS_PER_DAY}"),
            )
        }
    }

    /** A ppO₂ in millibar, as bar to two decimals. */
    fun bar(millibar: Int): AppText =
        AppText.named("unit_pressure_bar", AppText.written(formatDecimal(millibar / MBAR_PER_BAR, BAR_DECIMALS)))

    /** A deco model's name; one the app doesn't know reads as its token. */
    fun decoModel(model: String): AppText =
        when (model) {
            DecoModel.ZHL16C -> AppText.named("deco_model_zhl16c")
            DecoModel.VPM_B -> AppText.named("deco_model_vpm_b")
            DecoModel.VPM_B_GFS -> AppText.named("deco_model_vpm_b_gfs")
            DecoModel.DCIEM -> AppText.named("deco_model_dciem")
            else -> AppText.written(model)
        }

    /** A water type's name, or null for a token the app doesn't name. */
    fun water(type: String): AppText? =
        when (type) {
            WaterType.FRESH -> AppText.named("water_fresh")
            WaterType.SALT -> AppText.named("water_salt")
            WaterType.EN13319 -> AppText.named("water_en13319")
            WaterType.CUSTOM -> AppText.named("water_custom")
            else -> null
        }
}
