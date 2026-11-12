package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeEffort
import io.github.solcott.activitytracker.app.fixtures.fakeLeaderboardEntry
import io.github.solcott.activitytracker.app.fixtures.fakeMayorEntry
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.activitytracker.lexicon.app.SegmentElevationSource
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

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
            return state(ContentStates.loaded(named)).copy(names = PlaceNameFields(viewerDid = alice.did.value))
        }

    /** [renamed], for a viewer who may propose and whose session can't vote. */
    val votingUnavailable: SegmentState
        get() = renamed.let {
            val segment = checkNotNull(it.segment)
            it.copy(
                content = ContentStates.loaded(segment.copy(viewer = segment.viewer?.copy(canProposeName = true))),
                names = PlaceNameFields(canVoteHere = false, viewerDid = Fixed.CAROL.value),
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
                LIFT to lift,
                ELEVATION to elevation,
                ELEVATION_FROM_EFFORTS to elevationFromEfforts,
            )

    const val LIFT = "lift"
    const val ELEVATION = "elevation"
    const val ELEVATION_FROM_EFFORTS = "elevationFromEfforts"

    /**
     * A 1.6 km segment the AppView gave terrain elevation and a profile (segments/segment-elevation): 84 m up, 12 m down, a 4.5% average
     * grade, gentle, then a steep middle, then a short descent; the AppView measured its steepest stretch at 12.4%.
     */
    val elevation: SegmentState
        get() =
            state(
                    ContentStates.loaded(
                        segment.copy(
                            distanceMeters = 1_600,
                            elevationGain = 84,
                            elevationLoss = 12,
                            elevationSource = SegmentElevationSource.TERRAIN,
                        ),
                    ),
                )
                .copy(profile = fakeProfile(1_600, maxGradePercent = 12.4) { at -> CLIMB_START_M + climbProfile(at) })

    /** A segment whose elevation comes from athletes' efforts: the numbers, and no profile. */
    val elevationFromEfforts: SegmentState
        get() =
            state(
                ContentStates.loaded(
                    segment.copy(
                        distanceMeters = 1_600,
                        elevationGain = 84,
                        elevationLoss = 12,
                        elevationSource = SegmentElevationSource.EFFORTS,
                    ),
                ),
            )

    private const val CLIMB_START_M = 1_420.0

    /** Meters above the start [at] a fraction of the way along: 3% for 40%, 12% for 30%, 3% for 15%, then down 12 m. */
    @Suppress("MagicNumber") // The shape of one made-up climb.
    private fun climbProfile(at: Double): Double {
        val m = at * 1_600
        return when {
            m <= 640 -> m * 0.03
            m <= 1_120 -> 19.2 + (m - 640) * 0.12
            m <= 1_360 -> 76.8 + (m - 1_120) * 0.03
            else -> 84.0 - (m - 1_360) * 0.05
        }
    }

    /**
     * A [distanceMeters] profile of [PROFILE_POINTS] heights, [heightAt] each fraction along, with each point's grade, and the AppView's
     * [maxGradePercent] when it sent one.
     */
    private fun fakeProfile(distanceMeters: Long, maxGradePercent: Double? = null, heightAt: (Double) -> Double): SegmentProfile {
        val count = PROFILE_POINTS
        val heights = List(count) { heightAt(it.toDouble() / (count - 1)) }
        val spacing = distanceMeters.toDouble() / (count - 1)
        val grades =
            heights.indices.map { i ->
                val behind = (i - 2).coerceAtLeast(0)
                val ahead = (i + 2).coerceAtMost(heights.lastIndex)
                (heights[ahead] - heights[behind]) / ((ahead - behind) * spacing) * 100
            }
        return SegmentProfile(heights.toImmutableList(), distanceMeters, grades.toImmutableList(), maxGradePercent)
    }

    private const val PROFILE_POINTS = 128

    /** An active ski lift (segments/segment-lifts): the viewer's rides on it, and no leaderboard, Mayor or names. */
    val lift: SegmentState
        get() {
            val lift =
                fakeSegment(
                        "summit-quad",
                        record =
                            fakeSegmentRecord(
                                name = "Summit Quad",
                                sport = SegmentSportGroup.ALPINE,
                                source = SegmentSource.OSM,
                                license = "ODbL-1.0",
                                kind = SegmentKind.LIFT,
                            ),
                        distanceMeters = 1_420,
                        status = SegmentStatus.ACTIVE,
                    )
                    .copy(elevationGain = 310, elevationLoss = 0, elevationSource = SegmentElevationSource.TERRAIN)
            return SegmentState(
                content = ContentStates.loaded(lift),
                polyline = lift.record.line,
                efforts =
                    persistentListOf(
                        fakeEffort(fakeSegmentBasic(lift), elapsed = 412, startOffset = 600),
                        fakeEffort(fakeSegmentBasic(lift), elapsed = 430, startOffset = 1500),
                    ),
                starred = false,
                profile = fakeProfile(1_420) { at -> 1_850.0 + 310 * at },
            )
        }

    const val RENAMED = "renamed"
    const val VOTING_UNAVAILABLE = "votingUnavailable"
}

object SegmentMapSamples {
    private val content = SegmentMapContent(SegmentSamples.segment.record.line, SegmentSamples.segment.record.name, credit = null)

    /** An OpenStreetMap segment: its credit over the map's corner. */
    val credited: SegmentMapState
        get() = SegmentMapState(ContentStates.loaded(content.copy(credit = SegmentCredit.OpenStreetMap("ODbL-1.0"))))

    /** An agency's segment, naming the agency. */
    val agency: SegmentMapState
        get() = SegmentMapState(ContentStates.loaded(content.copy(credit = SegmentCredit.Agency("PD", "USFS"))))

    val all: Map<String, SegmentMapState>
        get() = contentSamples(null, content) { SegmentMapState(it) }

    /** Every shared sample and the credited ones, for the native page's previews and snapshots. */
    val variants: Map<String, SegmentMapState>
        get() = all + mapOf("credited" to credited, "agency" to agency)
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

    /**
     * States beyond content loading: a ride board with its e-bike type, an age group and a weight class chosen, four entries and the
     * viewer's own place at 140 (`filtered`), and the same choices while their first page loads (`firstPage`). Kept apart from [all], whose
     * keys are the content-loading conformance set.
     */
    val variants: Map<String, SegmentLeaderboardState>
        get() {
            val filtered =
                SegmentLeaderboardState(
                    name,
                    board = SegmentSportGroup.RIDE,
                    boards = persistentListOf(SegmentSportGroup.RIDE, SegmentBoard.E_BIKE),
                    ageGroup = LeaderboardAgeGroup.FROM_45,
                    weightClass = LeaderboardWeightClass.FROM_55,
                    entries =
                        PagedStates.end(
                            listOf(
                                fakeLeaderboardEntry(1, fakeProfileBasic(Fixed.BOB), elapsed = 230),
                                fakeLeaderboardEntry(2, fakeProfileBasic(Fixed.CAROL), elapsed = 262),
                                fakeLeaderboardEntry(3, fakeProfileBasic(DAVE, displayName = "Dave"), elapsed = 271),
                                fakeLeaderboardEntry(4, fakeProfileBasic(ERIN, displayName = "Erin"), elapsed = 3_725),
                            ),
                            meta,
                        ),
                    viewerEntry = fakeLeaderboardEntry(140, fakeProfileBasic(Fixed.ALICE), elapsed = 402),
                )
            return mapOf("filtered" to filtered, "firstPage" to filtered.copy(entries = PagedStates.loading(), viewerEntry = null))
        }
}

object SegmentMayorsSamples {
    val entries: ImmutableList<MayorEntry> =
        persistentListOf(fakeMayorEntry(1, fakeProfileBasic(Fixed.BOB), 14), fakeMayorEntry(2, fakeProfileBasic(Fixed.ALICE), 9))
    private val meta = BoardMeta<MayorEntry>(viewer = null)
    private val name = SegmentSamples.segment.record.name

    val all: Map<String, SegmentMayorsState>
        get() = pagedSamples(entries, meta) { SegmentMayorsState(name, entries = it) }

    /**
     * A hike segment's board for this week, with a walk type to choose and the viewer's own place at 58 (`filtered`). Kept apart from
     * [all], whose keys are the content-loading conformance set.
     */
    val variants: Map<String, SegmentMayorsState>
        get() =
            mapOf(
                "filtered" to
                    SegmentMayorsState(
                        name,
                        period = MayorPeriod.THIS_WEEK,
                        board = SegmentSportGroup.HIKE,
                        boards = persistentListOf(SegmentSportGroup.HIKE, Sport.WALK),
                        entries =
                            PagedStates.end(
                                listOf(
                                    fakeMayorEntry(1, fakeProfileBasic(Fixed.BOB), 1_204),
                                    fakeMayorEntry(2, fakeProfileBasic(Fixed.CAROL), 9),
                                ),
                                meta,
                            ),
                        viewerEntry = fakeMayorEntry(58, fakeProfileBasic(Fixed.ALICE), 1),
                    ),
            )
}

object StarredSegmentsSamples {
    val segments: ImmutableList<StarredSegment> =
        persistentListOf(
            StarredSegment(SegmentSamples.segment.uri, SegmentSamples.segment),
            fakeSegment("3ksegment2").let { StarredSegment(it.uri, it.copy(record = it.record.copy(name = "Flagstaff Climb"))) },
        )

    val all: Map<String, StarredSegmentsState>
        get() = pagedSamples(segments, Unit) { StarredSegmentsState(it, eventSink = {}) }

    /** A star whose segment is gone and one being unstarred, beside a ski lift. */
    private val mixed: ImmutableList<StarredSegment>
        get() {
            val gone = fakeSegment("3kgone").uri
            val lift =
                fakeSegment(
                    "3klift",
                    record = fakeSegmentRecord(name = "Summit Express", sport = SegmentSportGroup.ALPINE, kind = SegmentKind.LIFT),
                )
            return persistentListOf(segments.first(), StarredSegment(gone, null), StarredSegment(lift.uri, lift))
        }

    /**
     * States beyond content loading: a gone star being unstarred beside a ski lift (`unavailable`), a failed unstar over the stars
     * (`unstarFailed`), and a session that must sign in again (`needsSignIn`). Kept apart from [all], whose keys are the content-loading
     * conformance set.
     */
    val variants: Map<String, StarredSegmentsState>
        get() =
            mapOf(
                "unavailable" to
                    StarredSegmentsState(PagedStates.end(mixed, Unit), removing = persistentSetOf(mixed[1].uri), eventSink = {}),
                "unstarFailed" to
                    StarredSegmentsState(
                        PagedStates.end(segments, Unit),
                        actionError = UiError(UiError.Kind.UpdateStar, DataError.Network),
                        eventSink = {},
                    ),
                "needsSignIn" to StarredSegmentsState(PagedStates.loading(), needsSignIn = true, eventSink = {}),
            )
}

private val DAVE = Did("did:plc:dave")
private val ERIN = Did("did:plc:erin")

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
            name = textFieldStateOf(name),
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
