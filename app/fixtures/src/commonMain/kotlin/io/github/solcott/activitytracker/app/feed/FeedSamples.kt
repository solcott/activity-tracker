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
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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

    private fun state(content: ContentState<ImmutableList<ActivityView>>) =
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
        get() = state(ContentStates.loaded(activities))

    val refreshing: FeedState
        get() = state(ContentStates.refreshing(activities))

    val refreshFailed: FeedState
        get() = state(ContentStates.refreshFailed(activities))

    /** Loaded, with one change still uploading and two the server refused (data/outbox). */
    val unsent: FeedState
        get() = state(ContentStates.loaded(activities)).copy(pendingWrites = 1, rejectedChanges = 2)

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
            )
}
