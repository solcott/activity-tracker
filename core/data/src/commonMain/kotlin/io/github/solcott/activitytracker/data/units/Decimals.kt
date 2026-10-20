package io.github.solcott.activitytracker.data.units

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** The current locale's decimal separator, like `.` in en-US or `,` in de-DE. */
expect fun decimalSeparator(): Char

/** The platform's digit grouping separator for the current locale, or null when it reports none. */
internal expect fun platformGroupingSeparator(): Char?

/**
 * The current locale's digit grouping separator, like `,` in en-US or `.` in de-DE. Where the platform reports none (or a digit, as when a
 * locale doesn't group), a comma, or a point when the decimal separator is a comma.
 */
fun groupingSeparator(): Char =
    platformGroupingSeparator()?.takeUnless { it.isDigit() || it == decimalSeparator() } ?: if (decimalSeparator() == ',') '.' else ','

/**
 * Entered decimal text as a number, taking `,` or `.` as the separator whatever the locale, so `5,2` and `5.2` both work. Null when it
 * isn't a finite number (`toDoubleOrNull` accepts `NaN` and `Infinity`).
 */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/**
 * [value] rounded to [decimals] fraction digits, written with [separator] and its whole part grouped in thousands with [grouping]; common
 * code has no `String.format`. With [trimZeros], trailing zeros (and a bare separator) are dropped, so `61.50` reads `61.5` and `150.0`
 * reads `150`. Text the athlete will edit passes a null [grouping], so [parseDecimal] reads it back.
 */
fun formatDecimal(
    value: Double,
    decimals: Int,
    trimZeros: Boolean = false,
    separator: Char = decimalSeparator(),
    grouping: Char? = groupingSeparator(),
): String {
    val scale = DECIMAL_BASE.pow(decimals).toLong()
    val scaled = (value * scale).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = group(abs(scaled) / scale, grouping)
    val digits = if (decimals > 0) (abs(scaled) % scale).toString().padStart(decimals, '0') else ""
    val fraction = if (trimZeros) digits.trimEnd('0') else digits
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole$separator$fraction"
}

/** A whole number for people to read, grouped in thousands with [grouping]: `1959` reads `1,959` in en-US. */
fun formatWhole(value: Long, grouping: Char = groupingSeparator()): String = (if (value < 0) "-" else "") + group(abs(value), grouping)

/** A whole number, as [formatWhole]. */
fun formatWhole(value: Int, grouping: Char = groupingSeparator()): String = formatWhole(value.toLong(), grouping)

private fun group(whole: Long, grouping: Char?): String {
    val digits = whole.toString()
    if (grouping == null || digits.length <= GROUP_SIZE) return digits
    return digits.reversed().chunked(GROUP_SIZE).joinToString(grouping.toString()).reversed()
}

private const val GROUP_SIZE = 3

private const val DECIMAL_BASE = 10.0
