package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView
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

/** The cached feed for [mine] (the viewer's own) or Following, narrowed to [query]. */
internal fun FeedRepository.feed(mine: Boolean, viewerDid: String, query: ActivityFilter): Flow<List<ActivityView>> =
    if (mine) authorActivities(viewerDid, query) else timeline(query)

/** Refreshes [feed]'s first page from the AppView. */
internal suspend fun FeedRepository.refreshFeed(mine: Boolean, viewerDid: String, query: ActivityFilter) =
    if (mine) refreshAuthor(viewerDid, query) else refreshTimeline(query)
