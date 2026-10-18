package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.stat_air_temp
import io.github.solcott.activitytracker.composeApp.resources.stat_air_used
import io.github.solcott.activitytracker.composeApp.resources.stat_altitude
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.stat_cns
import io.github.solcott.activitytracker.composeApp.resources.stat_cns_value
import io.github.solcott.activitytracker.composeApp.resources.stat_desaturation
import io.github.solcott.activitytracker.composeApp.resources.stat_descent_time
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_number
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_time
import io.github.solcott.activitytracker.composeApp.resources.stat_end_n2
import io.github.solcott.activitytracker.composeApp.resources.stat_gas
import io.github.solcott.activitytracker.composeApp.resources.stat_gas_ccr
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_highest_ppo2
import io.github.solcott.activitytracker.composeApp.resources.stat_max_ascent_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_n2_value
import io.github.solcott.activitytracker.composeApp.resources.stat_no_fly
import io.github.solcott.activitytracker.composeApp.resources.stat_pressure_range
import io.github.solcott.activitytracker.composeApp.resources.stat_rmv
import io.github.solcott.activitytracker.composeApp.resources.stat_sac
import io.github.solcott.activitytracker.composeApp.resources.stat_surface_interval
import io.github.solcott.activitytracker.composeApp.resources.stat_visibility
import io.github.solcott.activitytracker.composeApp.resources.stat_water_temp
import io.github.solcott.activitytracker.composeApp.resources.stat_water_type
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_bar
import io.github.solcott.activitytracker.composeApp.resources.unit_rate_ft_min
import io.github.solcott.activitytracker.composeApp.resources.unit_rate_m_min
import io.github.solcott.activitytracker.composeApp.resources.water_custom
import io.github.solcott.activitytracker.composeApp.resources.water_en13319
import io.github.solcott.activitytracker.composeApp.resources.water_fresh
import io.github.solcott.activitytracker.composeApp.resources.water_salt
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.WaterType
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
        .plus(dive?.let { conditions(it) + diveSettingsStats(it) }.orEmpty())
        .toImmutableList()
}

/** The rest of what the dive computer or logbook kept: nitrogen, rates, the water and the surface, each only where stored. */
@Composable
private fun conditions(dive: ActivityDive): List<Stat> {
    val metric = LocalUnitSystem.current == UnitSystem.Metric
    return listOfNotNull(
        dive.endN2?.let { end ->
            Stat(stringResource(Res.string.stat_end_n2), stringResource(Res.string.stat_n2_value, dive.startN2 ?: 0, end))
        },
        dive.highestPpO2?.let {
            Stat(
                stringResource(Res.string.stat_highest_ppo2),
                stringResource(Res.string.unit_pressure_bar, formatDecimal(it / MBAR_PER_BAR, 2)),
            )
        },
        dive.maxAscentRate?.let { mmPerSecond ->
            val perMinute = UnitConversions.toElevationUnit(mmPerSecond / MM_PER_M * SECONDS_PER_MINUTE, LocalUnitSystem.current)
            Stat(
                stringResource(Res.string.stat_max_ascent_rate),
                stringResource(if (metric) Res.string.unit_rate_m_min else Res.string.unit_rate_ft_min, formatDecimal(perMinute, 0)),
            )
        },
        dive.descentTime?.let { Stat(stringResource(Res.string.stat_descent_time), Format.duration(it)) },
        dive.waterType?.let { type -> waterName(type)?.let { Stat(stringResource(Res.string.stat_water_type), stringResource(it)) } },
        dive.visibility?.let { Stat(stringResource(Res.string.stat_visibility), Format.depth(it / CM_PER_M)) },
        dive.airTemperature?.let { Stat(stringResource(Res.string.stat_air_temp), Format.temperature(it)) },
        dive.altitude?.let { Stat(stringResource(Res.string.stat_altitude), Format.elevation(it.toDouble())) },
        dive.noFlyTime?.let { Stat(stringResource(Res.string.stat_no_fly), DiveFormat.surfaceInterval((it / SECONDS_PER_MINUTE).toInt())) },
        dive.desaturationTime?.let {
            Stat(stringResource(Res.string.stat_desaturation), DiveFormat.surfaceInterval((it / SECONDS_PER_MINUTE).toInt()))
        },
    )
}

private fun waterName(type: String) =
    when (type) {
        WaterType.FRESH -> Res.string.water_fresh
        WaterType.SALT -> Res.string.water_salt
        WaterType.EN13319 -> Res.string.water_en13319
        WaterType.CUSTOM -> Res.string.water_custom
        else -> null
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
private const val MM_PER_M = 1000.0
private const val SECONDS_PER_MINUTE = 60
