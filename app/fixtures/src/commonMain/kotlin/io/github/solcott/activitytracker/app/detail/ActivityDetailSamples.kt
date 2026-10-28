package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeEffort
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object ActivityDetailSamples {
    val activity: ActivityView = fakeActivity()

    /** A page of [activity] as someone else sees it, with one comment and one segment effort. A new draft each time. */
    val page: ActivityDetailPage
        get() {
            val comment = fakeComment()
            return ActivityDetailPage(
                activity = activity,
                comments =
                    CommentsPaging(
                        items = persistentListOf(CommentThread(comment.uri, comment, persistentListOf())),
                        refresh = LoadStatus.Idle,
                        hasAnswer = true,
                        endReached = true,
                    ),
                sentComments = persistentListOf(),
                draftComment = TextFieldState(),
                viewerDid = Fixed.BOB,
                isOwn = false,
                likesList = null,
                eventSink = {},
                route = activity.record.polyline?.let { ShownRoute(it, gaps = null, ends = null) },
                segments = groupEfforts(listOf(fakeEffort(activity = activity.uri))),
            )
        }

    /**
     * Overlapping efforts from the Corner Canyon trails, grouped: a whole trail over its two stretches, a lone effort, and two parent
     * trails that start together, the shorter nested in the longer and over a section.
     */
    val nestedSegments: ImmutableList<SegmentEffortGroup>
        get() {
            fun effort(rkey: String, name: String, start: Long, elapsed: Long, rank: Int? = 1) =
                fakeEffort(
                    fakeSegmentBasic(fakeSegment(rkey, record = fakeSegmentRecord(name = name))),
                    activity.uri,
                    elapsed = elapsed,
                    rank = rank,
                    best = true,
                    startOffset = start,
                )
            return groupEfforts(
                listOf(
                    effort("3ksrattler", "Rattler Trail (eastbound)", 300, 908),
                    effort(
                        "3ksrattler1",
                        "Rattler Trail: Bonneville Shoreline Trail - Draper Section to Upper Rattler Connector Trail",
                        300,
                        534,
                    ),
                    effort("3ksrattler2", "Rattler Trail: Upper Rattler Connector Trail to Ghost Falls Trail", 834, 374),
                    effort("3ksghost", "Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road", 1300, 601),
                    effort("3ksrushcanyon", "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail", 2400, 492),
                    effort("3ksrushshore", "Rush Trail via Exit Stage Left Section to Bonneville Shoreline Trail", 2400, 574),
                    effort("3ksesl", "Rush Trail - Exit Stage Left Section", 2420, 89, rank = null),
                )
            )
        }

    val all: Map<String, ActivityDetailState>
        get() = contentSamples<ActivityDetailPage?, ActivityDetailState>(null, page) { ActivityDetailState(it, eventSink = {}) }

    /** The owner's own page. */
    val own: ActivityDetailState
        get() = ActivityDetailState(ContentStates.loaded(page.copy(viewerDid = Fixed.ALICE, isOwn = true)), eventSink = {})
}

object CombinePickerSamples {
    val candidates: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity("3kactivity3", fakeProfileBasic(), FeedSamples.activities.first().record.copy(title = "Ride (watch)"))
        )

    val all: Map<String, CombinePickerState>
        get() =
            contentSamples<ImmutableList<ActivityView>, CombinePickerState>(persistentListOf(), candidates) { CombinePickerState(it) } +
                (SampleKind.EMPTY to CombinePickerState(ContentStates.loaded<ImmutableList<ActivityView>>(persistentListOf())))
}
