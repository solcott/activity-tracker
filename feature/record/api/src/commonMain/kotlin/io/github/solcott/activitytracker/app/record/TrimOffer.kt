package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.trim.TrimTexts
import io.github.solcott.activitytracker.data.activity.trim.TrimSuggestion
import io.github.solcott.activitytracker.data.units.UnitSystem

/**
 * "Trim start or end" on an unsaved activity (feed/activity-trim), a recording's finish or an import's preview: what the app [suggestion]s
 * cutting, and whether a trim is [applied], which what's saved will have.
 */
@Immutable
data class TrimOffer(val suggestion: TrimSuggestion?, val applied: Boolean) {
    /** The line under the offer: that it's trimmed, else what the suggestion would cut, else nothing. */
    fun summary(units: UnitSystem): AppText? = TrimTexts.offer(suggestion, applied, units)
}
