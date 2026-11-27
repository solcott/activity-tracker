package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Immutable

/**
 * What a screen reader says for each thumb of an [AppRangeSlider]: its name ("Trim start") and its value as the screen words it ("12:30 in,
 * 7:42 AM"), in place of Material's "Range start" and a percentage.
 */
@Immutable data class RangeSliderLabels(val start: String, val startValue: String, val end: String, val endValue: String)
