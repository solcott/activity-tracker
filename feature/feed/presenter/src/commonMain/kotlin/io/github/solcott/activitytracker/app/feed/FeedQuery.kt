package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.dataresult.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The AppView filter for this feed filter, with its local calendar days resolved in [zone]: a range starts at the start of its first day
 * and ends at the start of the day after its last. A preset runs from its first day with no end, so it always includes [today].
 */
internal fun FeedFilter.toActivityFilter(today: LocalDate, zone: TimeZone): ActivityFilter {
    val (first, last) =
        when (val range = range) {
            null -> null to null
            is DateRange.LastDays -> today.minus(DatePeriod(days = range.days - 1)) to null
            DateRange.ThisYear -> LocalDate(today.year, 1, 1) to null
            is DateRange.Custom -> range.start to range.endInclusive
        }
    return ActivityFilter(
        types = types,
        since = first?.atStartOfDayIn(zone),
        until = last?.plus(DatePeriod(days = 1))?.atStartOfDayIn(zone),
    )
}

/**
 * The feed for [mine] (the viewer's own) or Following, narrowed to [query]: its cache, then the AppView's first page once refreshed, after
 * [beforeRefresh].
 */
internal fun FeedRepository.feedOutcomes(
    mine: Boolean,
    viewerDid: String,
    query: ActivityFilter,
    beforeRefresh: suspend () -> Unit,
): Flow<Outcome<List<ActivityView>>> = if (mine) authorOutcomes(viewerDid, query, beforeRefresh) else timelineOutcomes(query, beforeRefresh)
