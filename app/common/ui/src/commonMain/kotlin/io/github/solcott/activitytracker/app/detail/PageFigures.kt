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
 * [value] with its numbers in the value's own style and everything else (units, separators) at [unit]: "7.83" larger than "mi", split by
 * [FigureParts.of] as the native figure is. A value without a number, like a device's name, reads in the value's style throughout.
 */
internal fun withSmallUnits(value: String, unit: SpanStyle): AnnotatedString {
    val parts = FigureParts.of(value)
    if (parts.none { it.isNumber }) return AnnotatedString(parts.joinToString("") { it.text })
    return buildAnnotatedString { parts.forEach { if (it.isNumber) append(it.text) else withStyle(unit) { append(it.text) } } }
}
