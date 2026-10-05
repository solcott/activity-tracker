package io.github.solcott.activitytracker.data.units

import kotlin.math.roundToLong

/** Profile weights are stored in grams, and shown and entered in kg or, for imperial, lb. */
object WeightConversions {
    const val GRAMS_PER_KG = 1000.0
    const val GRAMS_PER_POUND = 453.59237

    /** Grams in one display weight unit (a kilogram or a pound). */
    fun gramsPerUnit(units: UnitSystem): Double =
        when (units) {
            UnitSystem.Metric -> GRAMS_PER_KG
            UnitSystem.Imperial -> GRAMS_PER_POUND
        }

    /** A profile weight in kg, or lb for imperial. */
    fun toWeightUnit(grams: Long, units: UnitSystem): Double = grams / gramsPerUnit(units)

    /** An entered weight (kg, or lb for imperial) in whole grams. */
    fun fromWeightUnit(value: Double, units: UnitSystem): Long = (value * gramsPerUnit(units)).roundToLong()
}
