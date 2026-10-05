package io.github.solcott.activitytracker.data.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitConversionsTest {
    private val epsilon = 1e-9

    @Test
    fun distance() {
        assertEquals(1609.344, UnitConversions.fromDistanceUnit(1.0, UnitSystem.Imperial), epsilon)
        assertEquals(5.0, UnitConversions.toDistanceUnit(5000.0, UnitSystem.Metric), epsilon)
        assertEquals(1.0, UnitConversions.toDistanceUnit(1609.344, UnitSystem.Imperial), epsilon)
    }

    @Test
    fun elevationInFeet() {
        assertEquals(1000.0, UnitConversions.toElevationUnit(304.8, UnitSystem.Imperial), epsilon)
        assertEquals(304.8, UnitConversions.toElevationUnit(304.8, UnitSystem.Metric), epsilon)
    }

    @Test
    fun speed() {
        // 10 m/s = 36 km/h ≈ 22.369 mph.
        assertEquals(36.0, UnitConversions.toSpeedUnit(10_000, UnitSystem.Metric), epsilon)
        assertEquals(22.369362920544, UnitConversions.toSpeedUnit(10_000, UnitSystem.Imperial), 1e-9)
    }

    @Test
    fun temperature() {
        assertEquals(32.0, UnitConversions.toTemperatureUnit(0, UnitSystem.Imperial), epsilon)
        assertEquals(-40.0, UnitConversions.toTemperatureUnit(-400, UnitSystem.Imperial), epsilon)
        assertEquals(-5.5, UnitConversions.toTemperatureUnit(-55, UnitSystem.Metric), epsilon)
        assertEquals(10.0, UnitConversions.fromTemperatureUnit(50.0, UnitSystem.Imperial), epsilon)
        assertEquals(-40.0, UnitConversions.fromTemperatureUnit(-40.0, UnitSystem.Imperial), epsilon)
    }

    @Test
    fun tokensAndRegionDefaults() {
        assertEquals(UnitSystem.Imperial, UnitSystem.fromToken("imperial"))
        assertNull(UnitSystem.fromToken("cubits"))
        assertNull(UnitSystem.fromToken(null))
        assertEquals(UnitSystem.Imperial, UnitSystem.defaultFor("US"))
        assertEquals(UnitSystem.Imperial, UnitSystem.defaultFor("us"))
        assertEquals(UnitSystem.Metric, UnitSystem.defaultFor("GB"))
        assertEquals(UnitSystem.Metric, UnitSystem.defaultFor(null))
    }

    @Test
    fun weightIsStoredInGrams() {
        assertEquals(68_039L, WeightConversions.fromWeightUnit(150.0, UnitSystem.Imperial))
        assertEquals(61_500L, WeightConversions.fromWeightUnit(61.5, UnitSystem.Metric))
        assertEquals(150.0, WeightConversions.toWeightUnit(68_039, UnitSystem.Imperial), 1e-3)
    }
}
