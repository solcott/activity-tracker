package io.github.solcott.activitytracker.activityfile

import kotlin.math.abs
import kotlin.math.roundToLong

/** Escapes text and attribute values for the export writers. */
internal fun String.escapeXml() =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

/**
 * At most [decimals] places, trailing zeros dropped, never in exponent form: `Double.toString` writes `1.0E-5` on the JVM, which isn't an
 * `xsd:decimal`.
 */
internal fun Double.toXmlDecimal(decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= DECIMAL_BASE }
    val scaled = (abs(this) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

private const val DECIMAL_BASE = 10
