package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CardRoute
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportCount
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.uistate.ContentState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object FeedSamples {
    val activities: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity(),
            fakeActivity(
                rkey = "3kactivity2",
                author = fakeProfileBasic(Fixed.BOB),
                record =
                    fakeActivityRecord(sport = Sport.RUN, title = "Lunch Run", distance = 8_200, movingTime = 2_460, elevationGain = 60),
                likeCount = 0,
                commentCount = 0,
            ),
        )

    /** Bob's history, imported as one batch: 312 activities over seven years (feed/import-cards). */
    val importCard: ImportCardView =
        ImportCardView(
            author = fakeProfileBasic(Fixed.BOB),
            batch = "3m5abcdefghij",
            count = 312,
            earliestStartedAt = Fixed.NOW - (7 * 365).days,
            latestStartedAt = Fixed.NOW - 30.days,
            sports = listOf(SportCount(Sport.RIDE, 201), SportCount(Sport.RUN, 98), SportCount(Sport.SWIM, 13)),
            distance = 14_380_000,
            routes = List(4) { CardRoute(Fixed.POLYLINE) },
            postedAt = Fixed.NOW - 2.hours,
        )

    private val items: ImmutableList<TimelineItem> = activities.map(TimelineItem::Activity).toImmutableList()

    private fun state(content: ContentState<ImmutableList<TimelineItem>>) =
        FeedState(
            content = content,
            error = null,
            pendingWrites = 0,
            viewerDid = Fixed.ALICE.value,
            canRecord = true,
            scope = FeedScope.Following,
            scopes = persistentListOf(FeedScope.Following, FeedScope.Mine),
            filter = FeedFilter.Default,
            filterScreen = FeedFilterScreen(),
            eventSink = {},
        )

    val loading: FeedState
        get() = state(ContentStates.loading(persistentListOf()))

    val failed: FeedState
        get() = state(ContentStates.failed(persistentListOf()))

    val empty: FeedState
        get() = state(ContentStates.loaded(persistentListOf()))

    val loaded: FeedState
        get() = state(ContentStates.loaded(items))

    val refreshing: FeedState
        get() = state(ContentStates.refreshing(items))

    val refreshFailed: FeedState
        get() = state(ContentStates.refreshFailed(items))

    /** Loaded, with Bob's import as one card above two activities. */
    val imported: FeedState
        get() =
            state(
                ContentStates.loaded(
                    persistentListOf(
                        TimelineItem.ImportCard(importCard),
                        TimelineItem.Activity(activities[0]),
                        TimelineItem.Activity(activities[1]),
                    )
                )
            )

    /** Loaded, with one change still uploading and two the server refused (data/outbox). */
    val unsent: FeedState
        get() = state(ContentStates.loaded(items)).copy(pendingWrites = 1, rejectedChanges = 2)

    /** Loaded, with an import's 3,000 changes waiting out the server's rate limit until 3:40 PM (data/outbox). */
    val paused: FeedState
        get() = state(ContentStates.loaded(items)).copy(pendingWrites = 3000, uploadPausedUntil = Fixed.NOW + 3.hours + 40.minutes)

    val all: Map<String, FeedState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.EMPTY to empty,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
                "unsent" to unsent,
                "paused" to paused,
                "imported" to imported,
            )
}
