package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** What gear is called and how its usage reads (dev/shared-display-text, gear/gear-usage). Numbers as the machine's locale writes them. */
class GearTextsTest {
    private fun totals(distance: Long = 0, movingTime: Long = 0, days: Long = 0, vertical: Long = 0) =
        GearTotals(activities = 1, movingTime, distance, elevationGain = 0, elevationLoss = vertical, days = days)

    private fun written(n: Long) = AppText.written(formatWhole(n))

    @Test
    fun aTypeIsNamedByItsTokenAndAnUnknownOneIsOther() {
        assertEquals(AppText.Named("gear_type_bike", fallback = AppText.named("gear_type_other")), GearTexts.type("bike"))
        assertEquals(AppText.Named("gear_type_other", fallback = AppText.named("gear_type_other")), GearTexts.type("hoverboard"))
    }

    @Test
    fun aKindFallsBackToItsType() =
        assertEquals(AppText.Named("gear_kind_bike_gravel", fallback = GearTexts.type("bike")), GearTexts.kind("bike", "gravel"))

    @Test
    fun distanceIsInWholeKilometersOrMiles() {
        assertEquals(
            AppText.named("unit_distance_km", written(3_200)),
            GearTexts.mainTotal(GearMainTotal.DISTANCE, totals(distance = 3_200_000), UnitSystem.Metric),
        )
        assertEquals(
            AppText.named("unit_distance_mi", written(1_988)),
            GearTexts.mainTotal(GearMainTotal.DISTANCE, totals(distance = 3_200_000), UnitSystem.Imperial),
        )
    }

    @Test
    fun skisReadDaysAndVertical() {
        val days = AppText.Plural("gear_total_days", 14, persistentListOf(written(14)))
        for (units in UnitSystem.entries) {
            assertEquals(
                AppText.named("gear_total_joined", days, StatText.elevation(41_000.0, units)),
                GearTexts.mainTotal(GearMainTotal.DAYS_AND_VERTICAL, totals(days = 14, vertical = 41_000), units),
            )
        }
    }

    @Test
    fun divesAndHours() {
        assertEquals(
            AppText.Plural("gear_total_dives", 12, persistentListOf(written(12))),
            GearTexts.mainTotal(GearMainTotal.DIVES, totals().copy(dives = 12), UnitSystem.Metric),
        )
        assertEquals(
            AppText.Plural("gear_total_dives", 0, persistentListOf(written(0))),
            GearTexts.mainTotal(GearMainTotal.DIVES, totals(), UnitSystem.Metric),
        )
        assertEquals(
            AppText.named("gear_total_hours", written(120)),
            GearTexts.mainTotal(GearMainTotal.TIME, totals(movingTime = 432_000), UnitSystem.Metric),
        )
    }

    @Test
    fun nothingCountedYet() =
        assertEquals(
            AppText.named("gear_total_none"),
            GearTexts.mainTotal(GearMainTotal.DISTANCE, totals().copy(activities = 0), UnitSystem.Imperial),
        )
}
