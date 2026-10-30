package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.deco_model_dciem
import io.github.solcott.activitytracker.composeApp.resources.deco_model_vpm_b
import io.github.solcott.activitytracker.composeApp.resources.deco_model_vpm_b_gfs
import io.github.solcott.activitytracker.composeApp.resources.deco_model_zhl16c
import io.github.solcott.activitytracker.composeApp.resources.dive_gradient_factors
import io.github.solcott.activitytracker.composeApp.resources.stat_deco_model
import io.github.solcott.activitytracker.composeApp.resources.stat_deco_model_gf
import io.github.solcott.activitytracker.composeApp.resources.stat_gradient_factors
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_critical
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_deco
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_limits
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_warn
import io.github.solcott.activitytracker.composeApp.resources.stat_safety_stop
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoint_high
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoint_low
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoints
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_bar
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import org.jetbrains.compose.resources.stringResource

/**
 * How the dive computer was set: its deco model (with ZHL-16C's gradient factors), ppO₂ limits, setpoints and safety stop, where stored.
 */
@Composable
internal fun diveSettingsStats(dive: ActivityDive): List<Stat> {
    val limits =
        listOfNotNull(
            dive.ppO2Warn?.let { stringResource(Res.string.stat_ppo2_warn, bar(it)) },
            dive.ppO2Critical?.let { stringResource(Res.string.stat_ppo2_critical, bar(it)) },
            dive.ppO2Deco?.let { stringResource(Res.string.stat_ppo2_deco, bar(it)) },
        )
    val setpoints =
        listOfNotNull(
            dive.lowSetpoint?.let { stringResource(Res.string.stat_setpoint_low, bar(it)) },
            dive.highSetpoint?.let { stringResource(Res.string.stat_setpoint_high, bar(it)) },
        )
    return listOfNotNull(
        decoModel(dive),
        limits.takeIf { it.isNotEmpty() }?.let { Stat(stringResource(Res.string.stat_ppo2_limits), it.joinToString(" · ")) },
        setpoints.takeIf { it.isNotEmpty() }?.let { Stat(stringResource(Res.string.stat_setpoints), it.joinToString(" · ")) },
        dive.safetyStopTime?.let { Stat(stringResource(Res.string.stat_safety_stop), Format.duration(it.toLong())) },
    )
}

/** The deco model, with ZHL-16C's gradient factors; the gradient factors alone for a dive that names no model. */
@Composable
private fun decoModel(dive: ActivityDive): Stat? {
    val gradientFactors =
        dive.gradientFactorLow?.let { low ->
            dive.gradientFactorHigh?.let { high -> stringResource(Res.string.dive_gradient_factors, low, high) }
        }
    val model = dive.decoModel?.let { decoModelName(it) }
    return when {
        model != null && gradientFactors != null && dive.decoModel == DecoModel.ZHL16C ->
            Stat(stringResource(Res.string.stat_deco_model), stringResource(Res.string.stat_deco_model_gf, model, gradientFactors))
        model != null -> Stat(stringResource(Res.string.stat_deco_model), model)
        else -> gradientFactors?.let { Stat(stringResource(Res.string.stat_gradient_factors), it) }
    }
}

/** Millibar as "1.40 bar". */
@Composable fun bar(millibar: Int): String = stringResource(Res.string.unit_pressure_bar, formatDecimal(millibar / MBAR_PER_BAR, 2))

/** A deco model's name; one the app doesn't know reads as its token. */
@Composable
fun decoModelName(model: String): String =
    when (model) {
        DecoModel.ZHL16C -> stringResource(Res.string.deco_model_zhl16c)
        DecoModel.VPM_B -> stringResource(Res.string.deco_model_vpm_b)
        DecoModel.VPM_B_GFS -> stringResource(Res.string.deco_model_vpm_b_gfs)
        DecoModel.DCIEM -> stringResource(Res.string.deco_model_dciem)
        else -> model
    }

private const val MBAR_PER_BAR = 1000.0
