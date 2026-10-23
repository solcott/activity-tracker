package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlinx.collections.immutable.persistentSetOf

object FeedFilterSamples {
    val default: FeedFilterState
        get() = FeedFilterState(filter = FeedFilter.Default, typePicker = null, customRange = null, eventSink = {})

    val narrowed: FeedFilterState
        get() = default.copy(filter = FeedFilter(types = persistentSetOf(ActivityType(Sport.RIDE)), range = DateRange.LastDays(30)))

    val all: Map<String, FeedFilterState>
        get() = mapOf(SampleKind.LOADED to default, "narrowed" to narrowed)
}
