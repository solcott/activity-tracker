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
                segments = persistentListOf(fakeEffort(activity = activity.uri)),
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
