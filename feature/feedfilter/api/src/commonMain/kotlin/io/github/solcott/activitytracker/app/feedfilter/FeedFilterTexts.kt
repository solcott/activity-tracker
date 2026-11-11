package io.github.solcott.activitytracker.app.feedfilter

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/**
 * What the feed filter bar's chips say (dev/shared-display-text), which the shared bar and the native one both resolve: the types chip and
 * the date chip, and each choice of the date menu.
 */
object FeedFilterTexts {
    /** All types when none is chosen, the one type when one is, else the first by sport then sub-sport and how many more. */
    fun types(filter: FeedFilter): AppText {
        val sorted = filter.types.sortedBy { it.sport + it.subSport.orEmpty() }
        return when (sorted.size) {
            0 -> AppText.named("feed_filter_all_types")
            1 -> LabelText.activityType(sorted.single())
            else -> {
                val more = sorted.size - 1
                AppText.Plural(
                    "feed_filter_types_more",
                    more,
                    persistentListOf(LabelText.activityType(sorted.first()), AppText.written(formatWhole(more.toLong()))),
                )
            }
        }
    }

    /** What the date chip says for [range], and each date menu choice: any time for none, else the range. */
    fun range(range: DateRange?): AppText =
        when (range) {
            null -> AppText.named("feed_filter_any_time")
            is DateRange.LastDays ->
                AppText.Plural("feed_filter_last_days", range.days, persistentListOf(AppText.written(formatWhole(range.days.toLong()))))
            DateRange.ThisYear -> AppText.named("feed_filter_this_year")
            is DateRange.Custom ->
                AppText.named(
                    "feed_filter_custom_range",
                    AppText.written(range.start.toString()),
                    AppText.written(range.endInclusive.toString()),
                )
        }

    /** The date menu's choices before Custom, in order: any time (null), then the presets. */
    fun rangeChoices(): List<DateRangeChoice> = (listOf<DateRange?>(null) + DateRange.Presets).map(::DateRangeChoice)

    /** Which date menu choice [range] checks: its place in [rangeChoices], or one past their end for Custom, which follows them. */
    fun chosenRange(range: DateRange?): Int = rangeChoices().indexOfFirst { it.range == range }.takeIf { it >= 0 } ?: rangeChoices().size
}

/**
 * One of the date menu's choices ([FeedFilterTexts.rangeChoices]): [range], or null for any time. An object, so Swift can hold a null one.
 */
@Immutable class DateRangeChoice(val range: DateRange?)
