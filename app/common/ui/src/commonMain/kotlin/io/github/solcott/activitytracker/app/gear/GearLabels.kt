package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType

// What each says is chosen by GearTexts (`:app-common-api`), shared with the native iOS UI; these resolve it.

/** A `GearType` value's name (`gear_type_<token>`); Other for one this client doesn't know. */
@Composable fun gearTypeLabel(type: String): String = GearTexts.type(type).string()

/** [kind]'s name within [type] (`gear_kind_<type>_<kind>`); the type's name for a kind this client doesn't know. */
@Composable fun gearKindLabel(type: String, kind: String): String = GearTexts.kind(type, kind).string()

/**
 * The one total the gear list shows for an item ([GearType.mainTotal]): "3,200 km", "14 days · 41,000 m", "12 dives" or "120 h", in the
 * athlete's units; "No activities yet" while nothing counts and it has no starting usage.
 */
@Composable
fun gearMainTotalLabel(main: GearMainTotal, totals: GearTotals): String =
    GearTexts.mainTotal(main, totals, LocalUnitSystem.current).string()

/** [meters] in whole kilometers or miles, per [LocalUnitSystem]: "3,200 km". */
@Composable fun gearDistanceLabel(meters: Long): String = GearTexts.distance(meters, LocalUnitSystem.current).string()

/** "14 days". */
@Composable fun gearDaysLabel(days: Long): String = GearTexts.days(days).string()

/** "12 dives". */
@Composable fun gearDivesLabel(dives: Long): String = GearTexts.dives(dives).string()

/** [seconds] as whole hours: "120 h". */
@Composable fun gearHoursLabel(seconds: Long): String = GearTexts.hours(seconds).string()
