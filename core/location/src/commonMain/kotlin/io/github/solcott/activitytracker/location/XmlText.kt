package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * This text without what XML 1.0 can't hold even as a reference: C0 controls other than tab, LF and CR, U+FFFE, U+FFFF and unpaired
 * surrogates, which xmlutil's writer refuses with an exception. The export writers pass every text and attribute value from the athlete
 * through it before the writer escapes it. Dropped rather than replaced with U+FFFD, since a stray control in a title carries nothing worth
 * showing.
 */
fun String.toXmlText(): String {
    val text = this
    if (text.all { it.isXmlChar() }) return text
    return buildString(length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isHighSurrogate() && text.getOrNull(i + 1)?.isLowSurrogate() == true) {
                append(c).append(text[i + 1])
                i += 2
                continue
            }
            if (c.isXmlChar()) append(c)
            i++
        }
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
fun Double.toXmlDecimal(decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= DECIMAL_BASE }
    val scaled = (abs(this) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

private const val DECIMAL_BASE = 10
