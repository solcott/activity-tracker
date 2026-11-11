package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

object FeedFilterSamples {
    val default: FeedFilterState
        get() = FeedFilterState(filter = FeedFilter.Default, typePicker = null, customRange = null, eventSink = {})

    val narrowed: FeedFilterState
        get() = default.copy(filter = FeedFilter(types = persistentSetOf(ActivityType(Sport.RIDE)), range = DateRange.LastDays(30)))

    val all: Map<String, FeedFilterState>
        get() = mapOf(SampleKind.LOADED to default, "narrowed" to narrowed)

    /** The type picker open over a trail run, with a part-picked category; for the native bar's sheet. */
    val picking: FeedFilterState
        get() {
            val trail = ActivityType(Sport.RUN, "trail")
            return default.copy(
                typePicker =
                    TypePickerState(
                        draft = persistentSetOf(trail),
                        quickTypes = persistentListOf(ActivityType(Sport.RUN), ActivityType(Sport.RIDE), ActivityType(Sport.WALK)),
                        categories =
                            persistentListOf(
                                CategorySelection(
                                    SportCategory.Running,
                                    persistentListOf(ActivityType(Sport.RUN), trail, ActivityType(Sport.RUN, "track")),
                                    Selection.Partial,
                                ),
                                CategorySelection(
                                    SportCategory.Cycling,
                                    persistentListOf(ActivityType(Sport.RIDE), ActivityType(Sport.RIDE, "road")),
                                    Selection.None,
                                ),
                            ),
                    )
            )
        }

    /** Several types and a custom range, so the chips' longest words show. */
    val custom: FeedFilterState
        get() =
            default.copy(
                filter =
                    FeedFilter(
                        types = persistentSetOf(ActivityType(Sport.RIDE), ActivityType(Sport.RUN, "trail"), ActivityType(Sport.WALK)),
                        range = DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 15)),
                    )
            )

    /** Beyond [all], which the shared screenshots render: what the native bar draws too. */
    val variants: Map<String, FeedFilterState>
        get() = all + mapOf("picking" to picking, "custom" to custom)
}
