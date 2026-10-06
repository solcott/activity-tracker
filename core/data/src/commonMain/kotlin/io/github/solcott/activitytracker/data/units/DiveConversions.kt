package io.github.solcott.activitytracker.data.units

/** A dive's tank pressures (bar) and gas volumes (liters) in display units. */
object DiveConversions {
    private const val PSI_PER_BAR = 14.503_773_8
    private const val CUBIC_FEET_PER_LITER = 0.035_314_666_7

    /** A tank pressure in bar, or psi for imperial. */
    fun toPressureUnit(bar: Double, units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> bar
            UnitSystem.Imperial -> bar * PSI_PER_BAR
        }

    /** A volume of gas in liters, or cubic feet for imperial. */
    fun toGasVolumeUnit(liters: Double, units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> liters
            UnitSystem.Imperial -> liters * CUBIC_FEET_PER_LITER
        }
}
