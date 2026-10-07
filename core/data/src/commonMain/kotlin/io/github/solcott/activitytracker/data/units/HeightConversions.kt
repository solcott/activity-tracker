package io.github.solcott.activitytracker.data.units

import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlin.math.roundToLong

/** Profile heights are stored in millimeters, and shown and entered in cm or, for imperial, feet and inches. */
object HeightConversions {
    const val MM_PER_CM = 10.0
    const val MM_PER_INCH = 25.4
    const val INCHES_PER_FOOT = 12

    /** A height in whole feet and inches, the inches rounded. */
    data class FeetInches(val feet: Long, val inches: Long)

    fun toCentimeters(mm: Long): Double = mm / MM_PER_CM

    fun toFeetInches(mm: Long): FeetInches {
        val inches = (mm / MM_PER_INCH).roundToLong()
        return FeetInches(inches / INCHES_PER_FOOT, inches % INCHES_PER_FOOT)
    }

    /** A profile height as editable text: whole or tenths of a cm, like `175` or `175.5` (`175,5` in de). */
    fun toCentimetersText(mm: Long): String = formatDecimal(toCentimeters(mm), decimals = 1, trimZeros = true)

    /** Entered cm in millimeters; null when blank or not a height the lexicon allows. */
    fun fromCentimetersText(text: String): Long? = parseDecimal(text)?.let { (it * MM_PER_CM).roundToLong() }?.takeIf(::inRange)

    /**
     * Entered feet and inches in millimeters; null when both are blank or it isn't a height the lexicon allows. One left blank counts as
     * zero, so `6` ft alone is six feet. Inches may have a decimal.
     */
    fun fromFeetInchesText(feet: String, inches: String): Long? {
        val bothBlank = feet.isBlank() && inches.isBlank()
        val ft = if (feet.isBlank()) 0L else feet.trim().toLongOrNull()?.takeIf { it >= 0 }
        val inch = if (inches.isBlank()) 0.0 else parseDecimal(inches)?.takeIf { it >= 0 }
        return if (bothBlank || ft == null || inch == null) {
            null
        } else {
            ((ft * INCHES_PER_FOOT + inch) * MM_PER_INCH).roundToLong().takeIf(::inRange)
        }
    }

    /** Blank (no height) or a height [fromCentimetersText] accepts. */
    fun isValidCentimetersText(text: String): Boolean = text.isBlank() || fromCentimetersText(text) != null

    /** Both blank (no height) or a height [fromFeetInchesText] accepts. */
    fun isValidFeetInchesText(feet: String, inches: String): Boolean =
        (feet.isBlank() && inches.isBlank()) || fromFeetInchesText(feet, inches) != null

    private fun inRange(mm: Long): Boolean = mm in 1..ProfileDetails.MAX_HEIGHT_MM
}
