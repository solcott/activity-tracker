package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.string

/** A figure's label in full, as the hero, cards and stats list name it ([FigureTexts.label]). */
@Composable internal fun figureLabel(figure: Figure): String = FigureTexts.label(figure).string()

@Composable internal fun groupTitle(kind: StatsGroupKind): String = FigureTexts.group(kind).string()

@Composable internal fun avgMaxLabel(metric: AvgMaxMetric): String = FigureTexts.avgMax(metric).string()

/** A figure's value in the viewer's units, with its unit, in full ([FigureTexts.value]). */
@Composable
internal fun figureText(value: FigureValue): String =
    FigureTexts.value(value, LocalUnitSystem.current, LocalClock.current.now(), LocalTimeZone.current).string()

/**
 * [value] with its numbers in the value's own style and everything else (units, separators) at [unit]: "7.83" larger than "mi". A value
 * without a number, like a device's name, reads in the value's style throughout.
 */
internal fun withSmallUnits(value: String, unit: SpanStyle): AnnotatedString {
    val text = unbreakableUnit(value)
    val numbers = NUMBER.findAll(text).toList()
    if (numbers.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var at = 0
        for (match in numbers) {
            if (match.range.first > at) withStyle(unit) { append(text.substring(at, match.range.first)) }
            append(match.value)
            at = match.range.last + 1
        }
        if (at < text.length) withStyle(unit) { append(text.substring(at)) }
    }
}

/**
 * [value] that a narrow column may break only between its number and its unit ("1:59" over "/100 yd"), never inside the unit: every space
 * but the first after the number is non-breaking, and nothing breaks after a slash.
 */
internal fun unbreakableUnit(value: String): String {
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
// Digits joined by decimal, grouping (including the no-break spaces some locales group with) and clock separators.
private val NUMBER = Regex("""[-−]?\d(?:[.,:\u00A0\u202F]?\d)*""")
