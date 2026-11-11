package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** [ImportBatchScreen]'s states: Bob's imported rides and runs over two months. */
object ImportBatchSamples {
    private val activities: ImmutableList<ActivityView> =
        listOf(10, 40)
            .mapIndexed { i, daysAgo ->
                fakeActivity(
                    rkey = "3kimport$i",
                    author = fakeProfileBasic(Fixed.BOB),
                    record =
                        fakeActivityRecord(
                            sport = if (i % 2 == 0) Sport.RIDE else Sport.RUN,
                            title = if (i % 2 == 0) "Evening Ride" else "Morning Run",
                            startedAt = Fixed.NOW - (365 + daysAgo).days,
                            // Without maps the cards are short, so a page's footer shows under them.
                            polyline = null,
                        ),
                    likeCount = 0,
                    commentCount = 0,
                )
            }
            .toImmutableList()

    val all: Map<String, ImportBatchState>
        get() = pagedSamples(activities, Unit) { ImportBatchState("Bob", it, viewerDid = Fixed.ALICE.value, eventSink = {}) }

    val loaded: ImportBatchState
        get() = all.getValue(SampleKind.LOADED)

    /** Three activities over three months, with routes. */
    private val withRoutes: ImmutableList<ActivityView> =
        listOf(10, 40, 75)
            .mapIndexed { i, daysAgo ->
                fakeActivity(
                    rkey = "3kimportroute$i",
                    author = fakeProfileBasic(Fixed.BOB),
                    record =
                        fakeActivityRecord(
                            sport = if (i % 2 == 0) Sport.RIDE else Sport.RUN,
                            title = if (i % 2 == 0) "Evening Ride" else "Morning Run",
                            startedAt = Fixed.NOW - (365 + daysAgo).days,
                        ),
                    likeCount = i.toLong(),
                    commentCount = 0,
                )
            }
            .toImmutableList()

    /**
     * States beyond the shared samples, which the native page's previews and snapshots draw too: three months of activities with routes,
     * and a like the session must sign in again to send.
     */
    val variants: Map<String, ImportBatchState>
        get() =
            mapOf(
                "threeMonths" to loaded.copy(activities = PagedStates.loaded(withRoutes)),
                "likeFailed" to loaded.copy(error = UiError(UiError.Kind.SignInAgain, null)),
            )
}
