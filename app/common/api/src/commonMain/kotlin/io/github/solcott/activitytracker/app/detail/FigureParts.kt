package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable

/** A run of a figure's value: a number ("7.83"), or what's between numbers (" mi", a unit, a separator). */
@Immutable data class FigurePart(val text: String, val isNumber: Boolean)

/**
 * A figure's value split into its numbers and the rest, so each UI draws its unit smaller than its number ("7.83" larger than "mi"), as the
 * hero and a card's headline do. Both UIs draw from this split, which also keeps a narrow column from wrapping inside a unit.
 */
object FigureParts {
    /** [value]'s runs in order, over [unbreakable] text; one run, not a number, when it holds no number (a device's name). */
    fun of(value: String): List<FigurePart> {
        val text = unbreakable(value)
        val numbers = NUMBER.findAll(text).toList()
        if (numbers.isEmpty()) return listOf(FigurePart(text, isNumber = false))
        return buildList {
            var at = 0
            for (match in numbers) {
                if (match.range.first > at) add(FigurePart(text.substring(at, match.range.first), isNumber = false))
                add(FigurePart(match.value, isNumber = true))
                at = match.range.last + 1
            }
            if (at < text.length) add(FigurePart(text.substring(at), isNumber = false))
        }
    }

    /**
     * [value] that a narrow column may break only between its number and its unit ("1:59" over "/100 yd"), never inside the unit: every
     * space but the first after the number is non-breaking, and nothing breaks after a slash.
     */
    fun unbreakable(value: String): String {
        val first = NUMBER.find(value)?.let { value.indexOf(' ', it.range.last) } ?: -1
        return buildString {
            value.forEachIndexed { i, c ->
                when {
                    c == ' ' && i != first -> append(NO_BREAK_SPACE)
                    c == '/' -> append(c).append(WORD_JOINER)
                    else -> append(c)
                }
            }
        }
    }

    private const val NO_BREAK_SPACE = '\u00A0'
    private const val WORD_JOINER = '\u2060'
}

// Digits joined by decimal, grouping (including the no-break spaces some locales group with) and clock separators.
private val NUMBER = Regex("""[-−]?\d(?:[.,:\u00A0\u202F]?\d)*""")
