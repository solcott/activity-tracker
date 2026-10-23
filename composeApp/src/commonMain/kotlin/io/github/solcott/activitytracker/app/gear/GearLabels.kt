package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.gear_total_days
import io.github.solcott.activitytracker.composeApp.resources.gear_total_dives
import io.github.solcott.activitytracker.composeApp.resources.gear_total_hours
import io.github.solcott.activitytracker.composeApp.resources.gear_total_joined
import io.github.solcott.activitytracker.composeApp.resources.gear_total_none
import io.github.solcott.activitytracker.composeApp.resources.gear_type_other
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_km
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_mi
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A `GearType` value's name (`gear_type_<token>`); Other for one this client doesn't know. */
@Composable
fun gearTypeLabel(type: String): String =
    Res.allStringResources["gear_type_${GearType.known(type)}"]?.let { stringResource(it) } ?: stringResource(Res.string.gear_type_other)

/** [kind]'s name within [type] (`gear_kind_<type>_<kind>`); the type's name for a kind this client doesn't know. */
@Composable
fun gearKindLabel(type: String, kind: String): String =
    Res.allStringResources["gear_kind_${type}_$kind"]?.let { stringResource(it) } ?: gearTypeLabel(type)

/**
 * The one total the gear list shows for an item ([GearType.mainTotal]): "3,200 km", "14 days · 41,000 m", "12 dives" or "120 h", in the
 * athlete's units; "No activities yet" while nothing counts and it has no starting usage.
 */
@Composable
fun gearMainTotalLabel(main: GearMainTotal, totals: GearTotals): String {
    if (totals.activities == 0L && totals.distance == 0L && totals.movingTime == 0L) return stringResource(Res.string.gear_total_none)
    return when (main) {
        GearMainTotal.DISTANCE -> gearDistanceLabel(totals.distance)
        GearMainTotal.DAYS_AND_VERTICAL ->
            stringResource(Res.string.gear_total_joined, gearDaysLabel(totals.days), Format.elevation(totals.elevationLoss.toDouble()))
        GearMainTotal.DIVES -> gearDivesLabel(totals.dives ?: 0)
        GearMainTotal.TIME -> gearHoursLabel(totals.movingTime)
    }
}

/** [meters] in whole kilometers or miles, per [LocalUnitSystem]: "3,200 km". */
@Composable
fun gearDistanceLabel(meters: Long): String {
    val units = LocalUnitSystem.current
    val value = formatWhole(UnitConversions.toDistanceUnit(meters.toDouble(), units).roundToLong())
    return when (units) {
        UnitSystem.Metric -> stringResource(Res.string.unit_distance_km, value)
        UnitSystem.Imperial -> stringResource(Res.string.unit_distance_mi, value)
    }
}

/** "14 days". */
@Composable
fun gearDaysLabel(days: Long): String = pluralStringResource(Res.plurals.gear_total_days, days.toPluralCount(), formatWhole(days))

/** "12 dives". */
@Composable
fun gearDivesLabel(dives: Long): String = pluralStringResource(Res.plurals.gear_total_dives, dives.toPluralCount(), formatWhole(dives))

/** [seconds] as whole hours: "120 h". */
@Composable
fun gearHoursLabel(seconds: Long): String =
    stringResource(Res.string.gear_total_hours, formatWhole((seconds / SECONDS_PER_HOUR).roundToLong()))

private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private const val SECONDS_PER_HOUR = 3600.0
