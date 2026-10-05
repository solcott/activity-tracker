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

    /** A profile weight as editable text: kg or lb with at most one decimal, like `61.5` or `150`. */
    fun toInputText(grams: Long, units: UnitSystem): String {
        val tenths = (toWeightUnit(grams, units) * TENTHS).roundToLong()
        return if (tenths % TENTHS_LONG == 0L) "${tenths / TENTHS_LONG}" else "${tenths / TENTHS_LONG}.${tenths % TENTHS_LONG}"
    }

    /** Entered weight text (kg, or lb for imperial) in grams; null when it isn't a weight the lexicon allows. */
    fun fromInputText(text: String, units: UnitSystem): Long? =
        text.trim().replace(',', '.').toDoubleOrNull()?.let { fromWeightUnit(it, units) }?.takeIf { it in 1..MAX_GRAMS }

    /** Blank (no weight) or a weight [fromInputText] accepts. */
    fun isValidInputText(text: String, units: UnitSystem): Boolean = text.isBlank() || fromInputText(text, units) != null

    /** The lexicon's maximum weight. */
    const val MAX_GRAMS = 500_000L

    private const val TENTHS = 10.0
    private const val TENTHS_LONG = 10L
}
