package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.page_stamina_min
import io.github.solcott.activitytracker.composeApp.resources.page_stamina_range
import io.github.solcott.activitytracker.composeApp.resources.unit_fluid_fl_oz
import io.github.solcott.activitytracker.composeApp.resources.unit_fluid_ml
import io.github.solcott.activitytracker.composeApp.resources.unit_percent
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

/** Milliliters, whole, or fluid ounces to one decimal for imperial. */
@Composable
internal fun fluidText(milliliters: Int): String {
    val units = LocalUnitSystem.current
    val value = UnitConversions.toFluidUnit(milliliters.toDouble(), units)
    return when (units) {
        UnitSystem.Metric -> stringResource(Res.string.unit_fluid_ml, formatWhole(value.roundToLong()))
        UnitSystem.Imperial -> stringResource(Res.string.unit_fluid_fl_oz, formatDecimal(value, FLUID_OUNCE_DECIMALS))
    }
}

private const val FLUID_OUNCE_DECIMALS = 1

/** "100% → 70% · min 48%", or whichever of them was recorded. */
@Composable
internal fun staminaText(value: FigureValue.Stamina): String {
    val start = value.start
    val end = value.end
    val range =
        if (start != null && end != null) stringResource(Res.string.page_stamina_range, start, end)
        else (start ?: end)?.let { stringResource(Res.string.unit_percent, it) }
    return listOfNotNull(range, value.min?.let { stringResource(Res.string.page_stamina_min, it) }).joinToString(" · ")
}
