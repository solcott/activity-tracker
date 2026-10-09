package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.stat_air_used
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.stat_cns
import io.github.solcott.activitytracker.composeApp.resources.stat_cns_value
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_number
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_time
import io.github.solcott.activitytracker.composeApp.resources.stat_gas
import io.github.solcott.activitytracker.composeApp.resources.stat_gas_ccr
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_pressure_range
import io.github.solcott.activitytracker.composeApp.resources.stat_rmv
import io.github.solcott.activitytracker.composeApp.resources.stat_sac
import io.github.solcott.activitytracker.composeApp.resources.stat_surface_interval
import io.github.solcott.activitytracker.composeApp.resources.stat_water_temp
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** A dive's feed card figures, from its record alone: how deep, how long, how cold. */
@Composable
fun diveCardStats(record: ActivityRecord): ImmutableList<Stat> =
    listOfNotNull(maxDepth(record.dive, null), diveTime(record), waterTemp(record)).toImmutableList()

/**
 * A dive's headline figures on its page, then the dive computer's details. A dive logged before records carried a summary takes its depths
 * from the depth chart ([streamMaxMeters], [streamAvgMeters]) when the viewer may see it.
 */
@Composable
fun diveOverviewStats(record: ActivityRecord, streamMaxMeters: Double?, streamAvgMeters: Double?): ImmutableList<Stat> {
    val dive = record.dive
    return listOfNotNull(
            maxDepth(dive, streamMaxMeters),
            diveTime(record),
            waterTemp(record),
            (dive?.avgDepth?.let { it / CM_PER_M } ?: streamAvgMeters)?.let {
                Stat(stringResource(Res.string.stat_avg_depth), Format.depth(it))
            },
            dive?.bottomTime?.let { Stat(stringResource(Res.string.stat_bottom_time), Format.duration(it)) },
            dive?.let { gas(it) },
            dive?.surfaceInterval?.let {
                Stat(stringResource(Res.string.stat_surface_interval), DiveFormat.surfaceInterval((it / SECONDS_PER_MINUTE).toInt()))
            },
            dive?.diveNumber?.let { Stat(stringResource(Res.string.stat_dive_number), "$it") },
            dive?.startCns?.let { start ->
                dive.endCns?.let { end -> Stat(stringResource(Res.string.stat_cns), stringResource(Res.string.stat_cns_value, start, end)) }
            },
            dive?.let { consumption(it) },
            dive?.let { airUsed(it) },
            record.avgHeartRate?.let { Stat(stringResource(Res.string.stat_heart_rate), stringResource(Res.string.unit_bpm, it)) },
        )
        .toImmutableList()
}

@Composable
private fun maxDepth(dive: ActivityDive?, streamMeters: Double?): Stat? =
    (dive?.maxDepth?.let { it / CM_PER_M } ?: streamMeters)?.let { Stat(stringResource(Res.string.stat_max_depth), Format.depth(it)) }

/** The whole dive, surface to surface: moving time means nothing underwater. */
@Composable
private fun diveTime(record: ActivityRecord): Stat = Stat(stringResource(Res.string.stat_dive_time), Format.duration(record.elapsedTime))

/** The coldest water, which divers plan for, else the average. */
@Composable
private fun waterTemp(record: ActivityRecord): Stat? =
    (record.dive?.minTemperature ?: record.avgTemperature)?.let { Stat(stringResource(Res.string.stat_water_temp), Format.temperature(it)) }

/** "EAN32 → Oxygen", or "CCR · Tx 18/45". */
@Composable
private fun gas(dive: ActivityDive): Stat? {
    val gases = dive.gases.orEmpty().map { DiveFormat.gasName(it.oxygen, it.helium) }.ifEmpty { null } ?: return null
    val names = gases.joinToString(" → ")
    return Stat(
        stringResource(Res.string.stat_gas),
        if (dive.closedCircuit == true) stringResource(Res.string.stat_gas_ccr, names) else names,
    )
}

/** The first tank's pressures, start to end. */
@Composable
private fun airUsed(dive: ActivityDive): Stat? {
    val tank = dive.tanks?.firstOrNull()
    val start = tank?.startPressure ?: return null
    return tank.endPressure?.let { end ->
        Stat(
            stringResource(Res.string.stat_air_used),
            stringResource(
                Res.string.stat_pressure_range,
                DiveFormat.pressure(start / MBAR_PER_BAR),
                DiveFormat.pressure(end / MBAR_PER_BAR),
            ),
        )
    }
}

/** By volume when the tank's size was known, else by pressure. */
@Composable
private fun consumption(dive: ActivityDive): Stat? =
    dive.rmv?.let { Stat(stringResource(Res.string.stat_rmv), DiveFormat.gasRate(it / ML_PER_L)) }
        ?: dive.sac?.let { Stat(stringResource(Res.string.stat_sac), DiveFormat.pressureRate(it / MBAR_PER_BAR)) }

private const val CM_PER_M = 100.0
private const val MBAR_PER_BAR = 1000.0
private const val ML_PER_L = 1000.0
private const val SECONDS_PER_MINUTE = 60
