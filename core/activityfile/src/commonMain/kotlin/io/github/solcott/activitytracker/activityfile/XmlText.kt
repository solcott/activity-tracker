package io.github.solcott.activitytracker.activityfile

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Escapes text and attribute values for the export writers, dropping what XML 1.0 can't hold even as a reference: C0 controls other than
 * tab, LF and CR, U+FFFE, U+FFFF and unpaired surrogates. Dropped rather than replaced with U+FFFD, since a stray control in a title
 * carries nothing worth showing.
 */
internal fun String.escapeXml(): String =
    buildString(length) {
        val text = this@escapeXml
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isHighSurrogate() && text.getOrNull(i + 1)?.isLowSurrogate() == true) {
                append(c).append(text[i + 1])
                i += 2
                continue
            }
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c.isXmlChar()) append(c)
            }
            i++
        }
    }

/** Whether XML 1.0 allows this UTF-16 unit on its own (a surrogate only as half of a pair). */
private fun Char.isXmlChar(): Boolean =
    when {
        isSurrogate() -> false
        this < ' ' -> this == '\t' || this == '\n' || this == '\r'
        else -> this != '\uFFFE' && this != '\uFFFF'
    }

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
