package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import kotlin.math.roundToLong
import kotlinx.collections.immutable.persistentListOf

/**
 * What gear is called and how its usage reads, chosen here for the shared and the native UI (dev/shared-display-text, gear/gear-usage).
 * Numbers are formatted here (`formatWhole`), so ui/number-format holds wherever the text is shown.
 */
object GearTexts {
    private const val SECONDS_PER_HOUR = 3600.0

    /** A `GearType` value's name (`gear_type_<token>`); Other for one this client doesn't know. */
    fun type(type: String): AppText = AppText.Named("gear_type_${GearType.known(type)}", fallback = AppText.named("gear_type_other"))

    /** [kind]'s name within [type] (`gear_kind_<type>_<kind>`); the type's name for a kind this client doesn't know. */
    fun kind(type: String, kind: String): AppText = AppText.Named("gear_kind_${type}_$kind", fallback = type(type))

    /**
     * The one total a list shows for an item ([GearType.mainTotal]): "3,200 km", "14 days · 41,000 m", "12 dives" or "120 h", in [units];
     * "No activities yet" while nothing counts and it has no starting usage.
     */
    fun mainTotal(main: GearMainTotal, totals: GearTotals, units: UnitSystem): AppText {
        if (totals.activities == 0L && totals.distance == 0L && totals.movingTime == 0L) return AppText.named("gear_total_none")
        return when (main) {
            GearMainTotal.DISTANCE -> distance(totals.distance, units)
            GearMainTotal.DAYS_AND_VERTICAL ->
                AppText.named("gear_total_joined", days(totals.days), StatText.elevation(totals.elevationLoss.toDouble(), units))
            GearMainTotal.DIVES -> dives(totals.dives ?: 0)
            GearMainTotal.TIME -> hours(totals.movingTime)
        }
    }

    /** [meters] in whole kilometers or miles: "3,200 km". */
    fun distance(meters: Long, units: UnitSystem): AppText {
        val value = AppText.written(formatWhole(UnitConversions.toDistanceUnit(meters.toDouble(), units).roundToLong()))
        return when (units) {
            UnitSystem.Metric -> AppText.named("unit_distance_km", value)
            UnitSystem.Imperial -> AppText.named("unit_distance_mi", value)
        }
    }

    /** "14 days". */
    fun days(days: Long): AppText =
        AppText.Plural("gear_total_days", days.toPluralCount(), persistentListOf(AppText.written(formatWhole(days))))

    /** "12 dives". */
    fun dives(dives: Long): AppText =
        AppText.Plural("gear_total_dives", dives.toPluralCount(), persistentListOf(AppText.written(formatWhole(dives))))

    /** [seconds] as whole hours: "120 h". */
    fun hours(seconds: Long): AppText =
        AppText.named("gear_total_hours", AppText.written(formatWhole((seconds / SECONDS_PER_HOUR).roundToLong())))

    private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
