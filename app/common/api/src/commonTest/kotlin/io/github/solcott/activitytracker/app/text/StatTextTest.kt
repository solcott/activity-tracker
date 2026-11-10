package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a stat reads in each unit system (dev/shared-display-text). Numbers are compared as the machine's locale writes them. */
class StatTextTest {
    private fun text(name: String, value: String) = AppText.named(name, AppText.written(value))

    @Test
    fun distance() {
        assertEquals(text("unit_distance_km", formatDecimal(4.83, 2)), StatText.distance(4_828.0, UnitSystem.Metric))
        assertEquals(text("unit_distance_mi", formatDecimal(3.0, 2)), StatText.distance(4_828.0, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.distance(null, UnitSystem.Metric))
    }

    @Test
    fun duration() {
        assertEquals("0:05", StatText.duration(5))
        assertEquals("10:00", StatText.duration(600))
        assertEquals("10:05:03", StatText.duration(36_303))
    }

    @Test
    fun pace() {
        assertEquals(text("unit_pace_per_km", "5:00"), StatText.pace(5_000.0, 1_500, UnitSystem.Metric))
        assertEquals(text("unit_pace_per_mi", "10:00"), StatText.pace(4_828.032, 1_800, UnitSystem.Imperial))
    }

    @Test
    fun paceOverNoDistanceIsMissing() {
        assertEquals(StatText.missing, StatText.pace(null, 600, UnitSystem.Metric))
        assertEquals(StatText.missing, StatText.pace(1_000.0, null, UnitSystem.Metric))
        assertEquals(StatText.missing, StatText.pace(5.0, 600, UnitSystem.Imperial))
    }

    @Test
    fun speed() {
        assertEquals(text("unit_speed_kmh", formatDecimal(36.0, 1)), StatText.speed(10_000, UnitSystem.Metric))
        assertEquals(text("unit_speed_mph", formatDecimal(22.4, 1)), StatText.speed(10_000, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.speed(null, UnitSystem.Metric))
    }

    @Test
    fun swimPace() {
        assertEquals(text("unit_pace_per_100m", "2:00"), StatText.swimPace(1_000.0, 1_200, UnitSystem.Metric))
        assertEquals(text("unit_pace_per_100yd", "1:50"), StatText.swimPace(1_000.0, 1_200, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.swimPace(null, null, UnitSystem.Metric))
    }

    @Test
    fun aSportsRate() {
        val units = UnitSystem.Metric
        assertEquals(AppText.named("stat_pace"), StatText.rate(Sport.RUN, 5_000.0, 1_500, units)?.label)
        assertEquals(text("unit_pace_per_km", "5:00"), StatText.rate(Sport.RUN, 5_000.0, 1_500, units)?.value)
        assertEquals(AppText.named("stat_speed"), StatText.rate(Sport.RIDE, 36_000.0, 3_600, units)?.label)
        assertEquals(text("unit_speed_kmh", formatDecimal(36.0, 1)), StatText.rate(Sport.RIDE, 36_000.0, 3_600, units)?.value)
        assertEquals(text("unit_pace_per_100m", "2:00"), StatText.rate(Sport.SWIM, 1_000.0, 1_200, units)?.value)
        assertNull(StatText.rate(Sport.YOGA, null, 3_600, units))
    }

    @Test
    fun elevationIsWholeAndGrouped() {
        assertEquals(text("unit_elevation_m", formatWhole(597L)), StatText.elevation(597.2, UnitSystem.Metric))
        assertEquals(text("unit_elevation_ft", formatWhole(1_959L)), StatText.elevation(597.2, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.elevation(null, UnitSystem.Imperial))
    }

    @Test
    fun depthKeepsADecimal() {
        assertEquals(text("unit_elevation_m", formatDecimal(18.3, 1)), StatText.depth(18.3, UnitSystem.Metric))
        assertEquals(text("unit_elevation_ft", formatDecimal(60.0, 1)), StatText.depth(18.3, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.depth(null, UnitSystem.Metric))
    }

    @Test
    fun temperature() {
        assertEquals(text("unit_temperature_c", formatDecimal(27.0, 1)), StatText.temperature(270, UnitSystem.Metric))
        assertEquals(text("unit_temperature_f", formatDecimal(80.6, 1)), StatText.temperature(270, UnitSystem.Imperial))
        assertEquals(StatText.missing, StatText.temperature(null, UnitSystem.Metric))
    }

    @Test
    fun weight() {
        assertEquals(text("unit_weight_kg", formatDecimal(70.0, 1)), StatText.weight(70_000, UnitSystem.Metric))
        assertEquals(text("unit_weight_lb", formatDecimal(154.3, 1)), StatText.weight(70_000, UnitSystem.Imperial))
    }
}
