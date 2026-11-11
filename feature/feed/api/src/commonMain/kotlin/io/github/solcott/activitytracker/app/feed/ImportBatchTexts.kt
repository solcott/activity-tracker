package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One month of a batch's activities (feed/import-cards): its heading ("Nov 2025") and its activities, in the order loaded. */
@Immutable data class ImportBatchMonth(val key: String, val heading: AppText, val activities: ImmutableList<ActivityView>)

/** What the batch page says and how it groups its activities, for the shared and the native page (dev/shared-display-text). */
object ImportBatchTexts {
    /** The page's title: who imported the batch, as its card showed them. */
    fun title(state: ImportBatchState): AppText = AppText.named("import_batch_title", AppText.written(state.name))

    /** [state]'s loaded activities by month, as [months] groups them. */
    fun months(state: ImportBatchState, zone: TimeZone): List<ImportBatchMonth> = months(state.activities.items, zone)

    /** [activities] by month of their start in the viewer's [zone], in the order they come (newest started first, as loaded). */
    fun months(activities: List<ActivityView>, zone: TimeZone): List<ImportBatchMonth> =
        activities
            .groupBy { it.effectiveRecord().startedAt.toLocalDateTime(zone).date.let { d -> d.year to d.month.ordinal + 1 } }
            .map { (month, inMonth) ->
                val (year, number) = month
                ImportBatchMonth(
                    key = "month-$year-$number",
                    // A year is a label, not a quantity: never grouped (ui/number-format).
                    heading = AppText.named("import_batch_month", AppText.named("month_short_$number"), AppText.written(year.toString())),
                    activities = inMonth.toImmutableList(),
                )
            }
}
