package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable

/** A calendar date, month 1–12. */
@Immutable data class HistoryDate(val year: Int, val month: Int, val day: Int)
