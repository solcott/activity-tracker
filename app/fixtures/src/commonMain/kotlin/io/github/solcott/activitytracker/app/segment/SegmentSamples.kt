package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeEffort
import io.github.solcott.activitytracker.app.fixtures.fakeLeaderboardEntry
import io.github.solcott.activitytracker.app.fixtures.fakeMayorEntry
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentBasic
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object SegmentSamples {
    val segment: SegmentView = fakeSegment()

    private fun state(content: ContentState<SegmentView?>) =
        SegmentState(
            content = content,
            polyline = segment.record.line,
            efforts = persistentListOf(fakeEffort(fakeSegmentBasic(segment), best = true)),
            starred = false,
        )

    val loading: SegmentState
        get() = SegmentState(content = ContentStates.loading(null))

    val failed: SegmentState
        get() = SegmentState(content = ContentStates.failed(null))

    val loaded: SegmentState
        get() = state(ContentStates.loaded(segment))

    val refreshing: SegmentState
        get() = state(ContentStates.refreshing(segment))

    val refreshFailed: SegmentState
        get() = state(ContentStates.refreshFailed(segment))

    val all: Map<String, SegmentState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
            )
}

object SegmentMapSamples {
    private val content = SegmentMapContent(SegmentSamples.segment.record.line, SegmentSamples.segment.record.name, credit = null)

    val all: Map<String, SegmentMapState>
        get() = contentSamples(null, content) { SegmentMapState(it) }
}

object SegmentLeaderboardSamples {
    val entries: ImmutableList<LeaderboardEntry> =
        persistentListOf(
            fakeLeaderboardEntry(1, fakeProfileBasic(Fixed.BOB), elapsed = 230),
            fakeLeaderboardEntry(2, fakeProfileBasic(Fixed.ALICE), elapsed = 245),
            fakeLeaderboardEntry(3, fakeProfileBasic(Fixed.CAROL), elapsed = 262),
        )
    private val meta = BoardMeta<LeaderboardEntry>(viewer = null)
    private val name = SegmentSamples.segment.record.name

    val all: Map<String, SegmentLeaderboardState>
        get() = pagedSamples(entries, meta) { SegmentLeaderboardState(name, entries = it) }
}

object SegmentMayorsSamples {
    val entries: ImmutableList<MayorEntry> =
        persistentListOf(fakeMayorEntry(1, fakeProfileBasic(Fixed.BOB), 14), fakeMayorEntry(2, fakeProfileBasic(Fixed.ALICE), 9))
    private val meta = BoardMeta<MayorEntry>(viewer = null)
    private val name = SegmentSamples.segment.record.name

    val all: Map<String, SegmentMayorsState>
        get() = pagedSamples(entries, meta) { SegmentMayorsState(name, entries = it) }
}

object StarredSegmentsSamples {
    val segments: ImmutableList<StarredSegment> =
        persistentListOf(
            StarredSegment(SegmentSamples.segment.uri, SegmentSamples.segment),
            fakeSegment("3ksegment2").let { StarredSegment(it.uri, it.copy(record = it.record.copy(name = "Flagstaff Climb"))) },
        )

    val all: Map<String, StarredSegmentsState>
        get() = pagedSamples(segments, Unit) { StarredSegmentsState(it, eventSink = {}) }
}

object CreateSegmentSamples {
    private fun loadedState(name: String = "Ridge climb") =
        CreateSegmentState(
            content = ContentStates.loaded(Unit),
            route = Fixed.POLYLINE,
            sport = "ride",
            lengthMeters = 2_400f,
            start = 0.2f,
            end = 0.6f,
            selection = SelectionCheck.Ok(SELECTED_METERS),
            name = TextFieldState(name),
            named = name.isNotBlank(),
        )

    val all: Map<String, CreateSegmentState>
        get() =
            mapOf(
                SampleKind.LOADING to CreateSegmentState(content = ContentStates.loading(Unit)),
                SampleKind.FAILED to CreateSegmentState(content = ContentStates.failed(Unit), loadErrorKind = UiError.Kind.LoadRoute),
                SampleKind.LOADED to loadedState(),
                SampleKind.INVALID to loadedState().copy(selection = SelectionCheck.TooShort(200)),
            )
}

/** The stretch chosen in the create-segment samples: 0.2 to 0.6 of a 2.4 km route. */
private const val SELECTED_METERS = 960.0
