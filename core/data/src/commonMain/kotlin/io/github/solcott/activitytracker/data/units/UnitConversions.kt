package io.github.solcott.activitytracker.data.units

import kotlin.math.roundToLong

/** Conversions between the SI integers stored in records and display or input units. */
object UnitConversions {
    const val METERS_PER_KM = 1000.0
    const val METERS_PER_MILE = 1609.344
    const val METERS_PER_FOOT = 0.3048

    /** Record speeds are mm/s; record temperatures are tenths of °C. */
    private const val MM_PER_M = 1000.0
    private const val SECONDS_PER_HOUR = 3600.0
    private const val TENTHS_PER_DEGREE = 10.0
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val FAHRENHEIT_OFFSET = 32.0

    /** Meters in one display distance unit (a kilometer or a mile). */
    fun metersPerDistanceUnit(units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> METERS_PER_KM
            UnitSystem.Imperial -> METERS_PER_MILE
        }

    fun toDistanceUnit(meters: Double, units: UnitSystem): Double = meters / metersPerDistanceUnit(units)

    fun fromDistanceUnit(value: Double, units: UnitSystem): Double = value * metersPerDistanceUnit(units)

    /** Meters, or feet for imperial. */
    fun toElevationUnit(meters: Double, units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> meters
            UnitSystem.Imperial -> meters / METERS_PER_FOOT
        }

    /** Average speed in mm/s, the record's unit; null without a distance or a positive duration. */
    fun averageSpeed(meters: Double?, seconds: Long?): Long? =
        if (meters != null && seconds != null && seconds > 0) (meters * MM_PER_M / seconds).roundToLong() else null

    /** km/h, or mph for imperial. */
    fun toSpeedUnit(mmPerSecond: Long, units: UnitSystem): Double = mmPerSecond / MM_PER_M * SECONDS_PER_HOUR / metersPerDistanceUnit(units)

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * FAHRENHEIT_PER_CELSIUS + FAHRENHEIT_OFFSET

    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - FAHRENHEIT_OFFSET) / FAHRENHEIT_PER_CELSIUS

    /** A record temperature (tenths of °C) in °C, or °F for imperial. */
    fun toTemperatureUnit(tenthsCelsius: Long, units: UnitSystem): Double {
        val celsius = tenthsCelsius / TENTHS_PER_DEGREE
        return when (units) {
            UnitSystem.Metric -> celsius
            UnitSystem.Imperial -> celsiusToFahrenheit(celsius)
        }
    }

    /** An entered temperature (°C, or °F for imperial) in °C. */
    fun fromTemperatureUnit(value: Double, units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> value
            UnitSystem.Imperial -> fahrenheitToCelsius(value)
        }
}
