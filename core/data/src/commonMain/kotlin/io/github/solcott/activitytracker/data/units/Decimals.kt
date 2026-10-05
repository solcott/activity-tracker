package io.github.solcott.activitytracker.data.units

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** The current locale's decimal separator, like `.` in en-US or `,` in de-DE. */
expect fun decimalSeparator(): Char

/**
 * Entered decimal text as a number, taking `,` or `.` as the separator whatever the locale, so `5,2` and `5.2` both work. Null when it
 * isn't a finite number (`toDoubleOrNull` accepts `NaN` and `Infinity`).
 */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/**
 * [value] rounded to [decimals] fraction digits, written with [separator]; common code has no `String.format`. With [trimZeros], trailing
 * zeros (and a bare separator) are dropped, so `61.50` reads `61.5` and `150.0` reads `150`.
 */
fun formatDecimal(value: Double, decimals: Int, trimZeros: Boolean = false, separator: Char = decimalSeparator()): String {
    val scale = DECIMAL_BASE.pow(decimals).toLong()
    val scaled = (value * scale).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = abs(scaled) / scale
    val digits = if (decimals > 0) (abs(scaled) % scale).toString().padStart(decimals, '0') else ""
    val fraction = if (trimZeros) digits.trimEnd('0') else digits
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole$separator$fraction"
}

private const val DECIMAL_BASE = 10.0
