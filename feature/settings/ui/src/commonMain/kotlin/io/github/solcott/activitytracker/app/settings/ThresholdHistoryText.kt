package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.string

// Text the threshold history screen shows, as `ThresholdHistoryTexts` chooses it for both UIs.

@Composable internal fun changeText(section: ThresholdHistorySection): String? = ThresholdHistoryTexts.change(section)?.string()

@Composable internal fun thresholdLabel(threshold: String): String = ThresholdHistoryTexts.label(threshold).string()

@Composable
internal fun thresholdValue(section: ThresholdHistorySection, value: Long): String = ThresholdHistoryTexts.value(section, value).string()

/** Where [row]'s value came from. */
@Composable internal fun sourceLabel(row: ThresholdHistoryRow): String = ThresholdHistoryTexts.source(row).string()
