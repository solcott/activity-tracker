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
import io.github.solcott.activitytracker.app.fixtures.fakeGroupPartner
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object ActivityDetailSamples {
    val activity: ActivityView = fakeActivity()

    /**
     * A page of [activity] as someone else sees it, with one comment, one segment effort and one athlete it was done with. A new draft each
     * time.
     */
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
                page =
                    ActivityPageContent(
                        start =
                            Figure(
                                FigureKey.StartTime,
                                FigureValue.StartTime(activity.record.startedAt.epochSeconds, activity.record.utcOffset, dateOnly = false),
                            )
                    ),
                route = activity.record.polyline?.let { ShownRoute(it, gaps = null, ends = null) },
                segments = groupEfforts(listOf(fakeEffort(activity = activity.uri))),
                partners = persistentListOf(fakeGroupPartner()),
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
                    effort("3ksrattler", "Rattler (eastbound)", 300, 908),
                    effort("3ksrattler1", "Rattler: Bonneville Shoreline - Draper → Upper Rattler Connector", 300, 534),
                    effort("3ksrattler2", "Rattler: Upper Rattler Connector → Ghost Falls", 834, 374),
                    effort("3ksghost", "Ghost Falls: Ghost Connector → Corner Canyon Road", 1300, 601),
                    effort("3ksrushcanyon", "Rush › Exit Stage Left → Canyon Hollow", 2400, 492),
                    effort("3ksrushshore", "Rush › Exit Stage Left → Bonneville Shoreline", 2400, 574),
                    effort("3ksesl", "Rush - Exit Stage Left", 2420, 89, rank = null),
                )
            )
        }

    /** A resort day's lifts (segments/segment-lifts): three rides on the quad, one on the gondola, in the order first ridden. */
    val liftRides: ImmutableList<LiftRides>
        get() {
            fun lift(rkey: String, name: String) =
                fakeSegmentBasic(
                    fakeSegment(rkey, record = fakeSegmentRecord(name = name, sport = SegmentSportGroup.ALPINE, kind = SegmentKind.LIFT))
                )
            val gondola = lift("base-gondola", "Base Gondola")
            val quad = lift("summit-quad", "Summit Quad")
            return liftRidesOf(
                listOf(
                    fakeEffort(gondola, activity.uri, elapsed = 540, startOffset = 0),
                    fakeEffort(quad, activity.uri, elapsed = 412, startOffset = 900),
                    fakeEffort(quad, activity.uri, elapsed = 430, startOffset = 1800),
                    fakeEffort(quad, activity.uri, elapsed = 405, startOffset = 2700),
                )
            )
        }

    /** A resort day's runs (segments/segment-lifts): two descents of an alpine run, the faster one the PR. */
    val skiRuns: ImmutableList<SegmentEffortGroup>
        get() {
            val run = fakeSegmentBasic(fakeSegment("lynx", record = fakeSegmentRecord(name = "Lynx", sport = SegmentSportGroup.ALPINE)))
            return groupEfforts(
                listOf(
                    fakeEffort(run, activity.uri, elapsed = 212, startOffset = 600, best = true),
                    fakeEffort(run, activity.uri, elapsed = 230, startOffset = 1500),
                )
            )
        }

    val all: Map<String, ActivityDetailState>
        get() = contentSamples<ActivityDetailPage?, ActivityDetailState>(null, page) { ActivityDetailState(it, eventSink = {}) }

    /** The owner's own page. */
    val own: ActivityDetailState
        get() =
            ActivityDetailState(
                ContentStates.loaded(page.copy(viewerDid = Fixed.ALICE, isOwn = true, ownGroup = OwnGroupState(left = false))),
                eventSink = {},
            )
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
