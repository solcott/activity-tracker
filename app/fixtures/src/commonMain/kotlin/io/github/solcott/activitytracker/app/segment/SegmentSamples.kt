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
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.uistate.ContentState
import kotlin.time.Duration.Companion.days
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

    /**
     * A segment the community renamed (segments/segment-names): its history, Bob's open proposal with the viewer's vote, and Alice's own.
     */
    val renamed: SegmentState
        get() {
            val alice = fakeProfileBasic(Fixed.ALICE)
            val bob = fakeProfileBasic(Fixed.BOB)
            val named =
                segment.copy(
                    record = segment.record.copy(name = "Trail 214 Connector"),
                    name = "Wiggly Woods",
                    nameHistory = listOf(SegmentNameAdoption("Wiggly Woods", Fixed.NOW - 40.days, up = 12, down = 2, proposer = bob)),
                    nameProposals =
                        listOf(
                            SegmentNameProposalView(
                                uri = Fixed.uri(bob.did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kproposal1"),
                                cid = Fixed.cid("3kproposal1"),
                                name = "Rooty Run",
                                proposer = bob,
                                openedAt = Fixed.NOW - 3.days,
                                up = 4,
                                down = 1,
                                viewerVote =
                                    SegmentNameViewerVote(
                                        Fixed.uri(alice.did, Nsid.FEED_SEGMENT_NAME_VOTE, "3kvote1"),
                                        SegmentNameVoteDirection.UP,
                                    ),
                            ),
                            SegmentNameProposalView(
                                uri = Fixed.uri(alice.did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kproposal2"),
                                cid = Fixed.cid("3kproposal2"),
                                name = "Rooty Ridge",
                                proposer = alice,
                                openedAt = Fixed.NOW - 1.days,
                                up = 2,
                                down = 0,
                            ),
                        ),
                    viewer = SegmentViewerState(canProposeName = false, canVoteOnNames = true),
                )
            return state(ContentStates.loaded(named)).copy(self = alice.did.value)
        }

    /** [renamed], for a viewer who may propose and whose session can't vote. */
    val votingUnavailable: SegmentState
        get() = renamed.let {
            val segment = checkNotNull(it.segment)
            it.copy(
                content = ContentStates.loaded(segment.copy(viewer = segment.viewer?.copy(canProposeName = true))),
                canVoteHere = false,
                self = Fixed.CAROL.value,
            )
        }

    val all: Map<String, SegmentState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
                RENAMED to renamed,
                VOTING_UNAVAILABLE to votingUnavailable,
            )

    const val RENAMED = "renamed"
    const val VOTING_UNAVAILABLE = "votingUnavailable"
}

object ProposeSegmentNameSamples {
    private fun state(name: String, editing: Boolean = false, unchanged: String = "Trail 214") =
        ProposeSegmentNameState(TextFieldState(name), editing, unchanged, sending = false, error = null, eventSink = {})

    val propose: ProposeSegmentNameState
        get() = state("Wiggly Woods")

    val edit: ProposeSegmentNameState
        get() = state("Rooty Ridge Loop", editing = true, unchanged = "Rooty Ridge")

    val invalid: ProposeSegmentNameState
        get() = state("trail 214")

    val all: Map<String, ProposeSegmentNameState>
        get() = mapOf(SampleKind.LOADED to propose, "edit" to edit, SampleKind.INVALID to invalid)
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
