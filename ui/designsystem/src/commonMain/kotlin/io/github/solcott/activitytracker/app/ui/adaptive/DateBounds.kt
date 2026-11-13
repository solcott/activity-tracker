package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Immutable
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The days an [AppDatePickerDialog] offers: [minDateMillis] to [maxDateMillis], both inclusive and compared per UTC day (the picker's
 * milliseconds), so any time inside the first or last day keeps that whole day. A null bound is open on that side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Immutable
data class DateBounds(val minDateMillis: Long? = null, val maxDateMillis: Long? = null) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val day = utcTimeMillis.floorDiv(MILLIS_PER_DAY)
        return (minDateMillis == null || day >= minDateMillis.floorDiv(MILLIS_PER_DAY)) &&
            (maxDateMillis == null || day <= maxDateMillis.floorDiv(MILLIS_PER_DAY))
    }

    override fun isSelectableYear(year: Int): Boolean =
        (minDateMillis == null || year >= utcYear(minDateMillis)) && (maxDateMillis == null || year <= utcYear(maxDateMillis))
}

private val MILLIS_PER_DAY = 1.days.inWholeMilliseconds

private fun utcYear(millis: Long): Int = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).year
