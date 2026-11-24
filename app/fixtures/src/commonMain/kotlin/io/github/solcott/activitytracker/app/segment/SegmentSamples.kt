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
import io.github.solcott.activitytracker.app.fixtures.fakeTrailAttributes
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.NotRanked
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
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailLit
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import io.github.solcott.activitytracker.location.SegmentGeometry
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
                TRAIL to trail,
                TRAIL_COMMUNITY to trailCommunity,
                TRAIL_SHEET to trailSheet,
                TRAIL_SUGGEST to trailSuggest,
                TRAIL_QUICK_CHECK to trailQuickCheck,
                TRAIL_INVITE to trailInvite,
            )

    const val LIFT = "lift"
    const val TRAIL = "trail"

    /**
     * An imported ride segment with Trail details (segments/trail-attributes): its surface, MTB scale and access rows, and no hiking
     * difficulty, which doesn't fit a ride.
     */
    val trail: SegmentState
        get() =
            state(
                    ContentStates.loaded(
                        segment.copy(
                            record = segment.record.copy(source = SegmentSource.OSM, license = "ODbL-1.0"),
                            trail = fakeTrailAttributes(),
                        ),
                    ),
                )
                .copy(credit = SegmentCredit.OpenStreetMap("ODbL-1.0"))

    const val TRAIL_COMMUNITY = "trailCommunity"
    const val TRAIL_SHEET = "trailSheet"
    const val TRAIL_SUGGEST = "trailSuggest"
    const val TRAIL_QUICK_CHECK = "trailQuickCheck"
    const val TRAIL_INVITE = "trailInvite"

    /**
     * [trail] as a rider sees it (segments/trail-detail-suggestions): each detail's source, riders' adopted dogs value, Maya's open
     * lighting suggestion, and the month riders last confirmed it.
     */
    val trailCommunity: SegmentState
        get() {
            val maya = fakeProfileBasic(Fixed.BOB).copy(displayName = "Maya Chen")
            val view =
                checkNotNull(trail.segment)
                    .copy(
                        trail = fakeTrailAttributes().copy(dog = TrailDog.NO),
                        trailDetails =
                            listOf(
                                TrailDetailInfo(TrailDetail.SAC_SCALE, TrailSource.OSM),
                                TrailDetailInfo(TrailDetail.MTB_SCALE, TrailSource.OSM),
                                TrailDetailInfo(TrailDetail.TRAIL_CLASS, TrailSource.USFS),
                                TrailDetailInfo(TrailDetail.SURFACE, TrailSource.OSM, confirmations = 8, confirmedMonth = "2026-10"),
                                TrailDetailInfo(TrailDetail.SMOOTHNESS, TrailSource.OSM),
                                TrailDetailInfo(TrailDetail.WIDTH, TrailSource.USFS),
                                TrailDetailInfo(TrailDetail.LIT, TrailSource.OSM, mayHaveChanged = true),
                                TrailDetailInfo(
                                    TrailDetail.DOG,
                                    TrailSource.COMMUNITY,
                                    riders = 12,
                                    confirmations = 3,
                                    confirmedMonth = "2026-11",
                                ),
                                TrailDetailInfo(TrailDetail.HORSE, TrailSource.OSM),
                                TrailDetailInfo(TrailDetail.SEASON, TrailSource.USFS),
                                TrailDetailInfo(TrailDetail.OPERATOR, TrailSource.OSM),
                                TrailDetailInfo(TrailDetail.WEBSITE, TrailSource.OSM),
                            ),
                        trailSuggestions =
                            listOf(
                                TrailDetailSuggestionView(
                                    uri = Fixed.uri(maya.did, Nsid.FEED_TRAIL_DETAIL_SUGGESTION, "3ksuggest1"),
                                    cid = Fixed.cid("3ksuggest1"),
                                    detail = TrailDetail.LIT,
                                    value = TrailAttributes(lit = TrailLit.NO),
                                    author = maya,
                                    openedAt = Fixed.NOW - 5.days,
                                    up = 4,
                                    down = 1,
                                ),
                            ),
                        viewer = SegmentViewerState(canVoteOnTrailDetails = true, canSuggestTrailDetails = true, recentEffort = true),
                    )
            return trail.copy(content = ContentStates.loaded(view))
        }

    /** [trailCommunity] with the lighting sheet open: its source, the viewer's answer, and Maya's suggestion. */
    val trailSheet: SegmentState
        get() = trailCommunity.let {
            it.copy(trail = TrailFields(sheet = TrailContributionTexts.sheet(checkNotNull(it.segment), TrailDetail.LIT, Fixed.ALICE.value)))
        }

    /** [trailCommunity] with the picker suggesting a width open, Doubletrack chosen. */
    val trailSuggest: SegmentState
        get() = trailCommunity.let {
            it.copy(
                trail = TrailFields(picker = TrailContributionTexts.picker(checkNotNull(it.segment), TrailDetail.WIDTH).copy(selected = 1)),
            )
        }

    /** [trailCommunity] after a recent ride: the quick check, surface answered still right. */
    val trailQuickCheck: SegmentState
        get() = trailCommunity.let { state ->
            val check = checkNotNull(TrailContributionTexts.quickCheck(checkNotNull(state.segment)))
            state.copy(
                trail =
                    TrailFields(
                        quickCheck =
                            check.copy(
                                rows =
                                    check.rows
                                        .map { if (it.key == TrailDetail.SURFACE) it.copy(answer = TrailDetailVoteDirection.STILL) else it }
                                        .toImmutableList(),
                            ),
                    ),
            )
        }

    /** An athlete's segment with no details, to a regular: the invitation to describe it. */
    val trailInvite: SegmentState
        get() {
            val view = segment.copy(viewer = SegmentViewerState(canVoteOnTrailDetails = true, canSuggestTrailDetails = true))
            return state(ContentStates.loaded(view))
                .copy(trail = TrailFields(missing = TrailContributionTexts.missing(view).toImmutableList()))
        }

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

    /**
     * An active OpenStreetMap segment the viewer starred, as the Segment page's whole layout shows it (segments/segment-page): a profile,
     * its leaderboard's leader and the viewer's place, its Mayor and the viewer's place on that board, the trail it lies on, Trail details
     * in all three cards, and the viewer's efforts: their PR in 3rd, one from an activity excluded from leaderboards, and an e-bike ride's.
     */
    val openStreetMap: SegmentState
        get() {
            val osm =
                fakeSegment(
                        "osm-climb",
                        record = fakeSegmentRecord(name = "Notch Road Climb", source = SegmentSource.OSM, license = "ODbL-1.0"),
                        distanceMeters = 1_600,
                        status = SegmentStatus.ACTIVE,
                    )
                    .copy(
                        elevationGain = 84,
                        elevationLoss = 12,
                        elevationSource = SegmentElevationSource.TERRAIN,
                        paths = listOf(NOTCH_TRAIL.uri),
                        trail = fakeTrailAttributes().copy(trailClass = 2, gradePercent = 6),
                    )
            val basic = fakeSegmentBasic(osm)
            return SegmentState(
                content = ContentStates.loaded(osm),
                polyline = osm.record.line,
                efforts =
                    persistentListOf(
                        fakeEffort(basic, elapsed = 312, startedOn = "2026-05-31", rank = 3, best = true),
                        fakeEffort(basic, elapsed = 330, startedOn = "2026-05-12", startOffset = 1_200)
                            .copy(notRanked = NotRanked.EXCLUDED),
                        fakeEffort(basic, elapsed = 3_725, startedOn = "2026-04-02", startOffset = 1_800).copy(eBike = true),
                    ),
                starred = true,
                credit = SegmentCredit.OpenStreetMap("ODbL-1.0"),
                mayor =
                    MayorSummary(
                        leader = fakeMayorEntry(athlete = fakeProfileBasic(Fixed.BOB), efforts = 14),
                        viewer = fakeMayorEntry(rank = 4, efforts = 3),
                    ),
                leaderboard =
                    LeaderboardSummary(
                        leader = fakeLeaderboardEntry(athlete = fakeProfileBasic(Fixed.CAROL), elapsed = 252),
                        viewer = fakeLeaderboardEntry(rank = 3, elapsed = 312),
                    ),
                trails = persistentListOf(NOTCH_TRAIL),
                profile = fakeProfile(1_600, maxGradePercent = 12.4) { at -> CLIMB_START_M + climbProfile(at) },
            )
        }

    /** [openStreetMap] with both boards' reads failed (segments/segment-leaderboards): the Leaderboard card alone, with no leader. */
    val boardsFailed: SegmentState
        get() = openStreetMap.copy(leaderboard = null, mayor = null)

    private val NOTCH_TRAIL = SegmentTrail(Fixed.uri(Did("did:plc:importer"), Nsid.FEED_PATH, "notch-road"), "Notch Road")

    /** A duplicate of an earlier segment, from an agency's trail data: its status and Open the original, and no efforts. */
    val duplicate: SegmentState
        get() {
            val copy =
                fakeSegment(
                        "agency-dup",
                        record = fakeSegmentRecord(name = "Ridge Trail", source = SegmentSource.AGENCY, license = "CC0-1.0"),
                        status = SegmentStatus.DUPLICATE,
                    )
                    .copy(duplicateOf = segment.uri)
            return SegmentState(
                content = ContentStates.loaded(copy),
                polyline = copy.record.line,
                credit = SegmentCredit.Agency("CC0-1.0", attribution = "US Forest Service"),
            )
        }

    /**
     * A ride segment whose segment group (segments/segment-groups) also runs as run and hike segments, each both ways: the page offers the
     * three sport groups and the two directions.
     */
    val group: SegmentState
        get() {
            val line = segment.record.line
            val back = SegmentGeometry.encodePolyline(SegmentGeometry.decodePolyline(line).orEmpty().reversed())
            val members =
                listOf(
                        SegmentSportGroup.RIDE to back,
                        SegmentSportGroup.RUN to line,
                        SegmentSportGroup.RUN to back,
                        SegmentSportGroup.HIKE to line,
                        SegmentSportGroup.HIKE to back,
                    )
                    .mapIndexed { i, (sport, way) ->
                        GetSegment.GroupMember(fakeSegment("3kroute$i").uri, sport, name = segment.record.name, line = way)
                    }
            return loaded.copy(group = GroupSwitcher.of(segment, members))
        }

    /**
     * The one-way ride segment "Clarks" (segments/segment-direction), alone in its segment group and clearly climbing: the page shows no
     * sport choice and its one direction as "Uphill only", which can't be chosen.
     */
    val oneWay: SegmentState
        get() {
            val clarks =
                segment.copy(
                    record = segment.record.copy(name = "Clarks", source = SegmentSource.OSM, license = "ODbL-1.0", oneWay = true),
                    distanceMeters = 2_287,
                    elevationGain = 199,
                    elevationLoss = 0,
                    oneWay = true,
                )
            return loaded.copy(content = ContentStates.loaded(clarks), group = GroupSwitcher.of(clarks, emptyList()))
        }

    /** [group] on a line that clearly climbs from its start: the directions read Uphill and Downhill. */
    val groupByClimb: SegmentState
        get() {
            val climbing = segment.copy(elevationGain = 120, elevationLoss = 5)
            val members = checkNotNull(group.group).options.filter { it.uri != segment.uri }
            val line = segment.record.line
            val back = SegmentGeometry.encodePolyline(SegmentGeometry.decodePolyline(line).orEmpty().reversed())
            val groupMembers = members.map {
                GetSegment.GroupMember(it.uri, it.sport, name = segment.record.name, line = if (it.forward) line else back)
            }
            return loaded.copy(content = ContentStates.loaded(climbing), group = GroupSwitcher.of(climbing, groupMembers))
        }

    /** Kept apart from [all], whose keys are the content-loading set. */
    val variants: Map<String, SegmentState>
        get() =
            mapOf(
                OPEN_STREET_MAP to openStreetMap,
                BOARDS_FAILED to boardsFailed,
                DUPLICATE to duplicate,
                GROUP to group,
                ONE_WAY to oneWay,
                GROUP_BY_CLIMB to groupByClimb,
            )

    const val RENAMED = "renamed"
    const val VOTING_UNAVAILABLE = "votingUnavailable"
    const val OPEN_STREET_MAP = "openStreetMap"
    const val BOARDS_FAILED = "boardsFailed"
    const val DUPLICATE = "duplicate"
    const val GROUP = "group"
    const val ONE_WAY = "oneWay"
    const val GROUP_BY_CLIMB = "groupByClimb"
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
     * viewer's own place at 140, pinned with their gap to first (`filtered`), the same choices while their first page loads (`firstPage`),
     * and the viewer second, among the loaded rows (`ownRow`). Kept apart from [all], whose keys are the content-loading conformance set.
     */
    val variants: Map<String, SegmentLeaderboardState>
        get() {
            val viewer = fakeLeaderboardEntry(140, fakeProfileBasic(Fixed.ALICE), elapsed = 402)
            val filtered =
                SegmentLeaderboardState(
                    name,
                    filters =
                        BoardFilterFields(
                            BoardKind.Leaderboard,
                            board = SegmentSportGroup.RIDE,
                            boards = persistentListOf(SegmentSportGroup.RIDE, SegmentBoard.E_BIKE),
                            ageGroup = LeaderboardAgeGroup.FROM_45,
                            weightClass = LeaderboardWeightClass.FROM_55,
                        ),
                    entries =
                        PagedStates.end(
                            listOf(
                                fakeLeaderboardEntry(1, fakeProfileBasic(Fixed.BOB), elapsed = 230),
                                fakeLeaderboardEntry(2, fakeProfileBasic(Fixed.CAROL), elapsed = 262),
                                fakeLeaderboardEntry(3, fakeProfileBasic(DAVE, displayName = "Dave"), elapsed = 271),
                                fakeLeaderboardEntry(4, fakeProfileBasic(ERIN, displayName = "Erin"), elapsed = 3_725),
                            ),
                            BoardMeta(viewer),
                        ),
                    viewerEntry = viewer,
                )
            val ownRow = SegmentLeaderboardState(name, entries = PagedStates.loaded(entries, BoardMeta(entries[1]), PagedStates.NEXT))
            return mapOf(
                "filtered" to filtered,
                "firstPage" to filtered.copy(entries = PagedStates.loading(), viewerEntry = null),
                "ownRow" to ownRow,
            )
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
     * A hike segment's board for this week, with a walk type to choose and the viewer's own place at 58, pinned with what it takes to be
     * Mayor (`filtered`), and the viewer second, among the loaded rows (`ownRow`). Kept apart from [all], whose keys are the
     * content-loading conformance set.
     */
    val variants: Map<String, SegmentMayorsState>
        get() =
            mapOf(
                "ownRow" to SegmentMayorsState(name, entries = PagedStates.end(entries, BoardMeta(entries[1]))),
                "filtered" to
                    SegmentMayorsState(
                        name,
                        filters =
                            BoardFilterFields(
                                BoardKind.Mayors,
                                period = MayorPeriod.THIS_WEEK,
                                board = SegmentSportGroup.HIKE,
                                boards = persistentListOf(SegmentSportGroup.HIKE, Sport.WALK),
                            ),
                        entries =
                            PagedStates.end(
                                listOf(
                                    fakeMayorEntry(1, fakeProfileBasic(Fixed.BOB), 1_204),
                                    fakeMayorEntry(2, fakeProfileBasic(Fixed.CAROL), 9),
                                ),
                                BoardMeta(MAYORS_VIEWER),
                            ),
                        viewerEntry = MAYORS_VIEWER,
                    ),
            )
}

/** The viewer on the filtered Mayors board, 58th with one effort. */
private val MAYORS_VIEWER = fakeMayorEntry(58, fakeProfileBasic(Fixed.ALICE), 1)

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
            lengthMeters = ROUTE_METERS,
            start = ROUTE_METERS * 0.2f,
            end = ROUTE_METERS * 0.6f,
            selection = SelectionCheck.Ok(SELECTED_METERS),
            highlight = HIGHLIGHT,
            name = textFieldStateOf(name),
            named = name.isNotBlank(),
        )

    private fun unavailable(reason: CreateSegmentUnavailable) =
        CreateSegmentState(content = ContentStates.loaded(Unit), unavailable = reason)

    val all: Map<String, CreateSegmentState>
        get() =
            mapOf(
                SampleKind.LOADING to CreateSegmentState(content = ContentStates.loading(Unit)),
                SampleKind.FAILED to CreateSegmentState(content = ContentStates.failed(Unit), loadErrorKind = UiError.Kind.LoadRoute),
                SampleKind.LOADED to loadedState(),
                SampleKind.INVALID to loadedState().copy(selection = SelectionCheck.TooShort(200), highlight = null),
            )

    /**
     * The states beyond these the native form draws: unnamed, the other two checks, a segment naming its activity, a duplicate, the
     * allowance, publishing, a failed check, and each reason no segment can be made.
     */
    val variants: Map<String, CreateSegmentState>
        get() =
            mapOf(
                "unnamed" to loadedState(name = ""),
                "tooLong" to loadedState().copy(selection = SelectionCheck.TooLong(MAX_METERS), highlight = null),
                "crossesGap" to loadedState().copy(selection = SelectionCheck.CrossesGap, highlight = null),
                "namesActivity" to loadedState().copy(namesActivity = true),
                "duplicate" to
                    loadedState().copy(duplicates = persistentListOf(fakeSegment(record = fakeSegmentRecord(name = "Ridge Trail climb")))),
                "limitReached" to loadedState().copy(limitReached = true),
                "publishing" to loadedState().copy(publishing = true),
                "checkFailed" to loadedState().copy(actionError = UiError(UiError.Kind.CheckSegment)),
                "noRoute" to unavailable(CreateSegmentUnavailable.NoRoute),
                "noSportGroup" to unavailable(CreateSegmentUnavailable.NoSportGroup),
                "allHidden" to unavailable(CreateSegmentUnavailable.AllHidden),
            )
}

/** The route in the create-segment samples is taken as 2.4 km, and the stretch chosen 0.2 to 0.6 of it. */
private const val ROUTE_METERS = 2_400f
private const val SELECTED_METERS = 960.0
private const val MAX_METERS = 50_000

/** The stretch drawn over the samples' route: its first leg, the first two points of [Fixed.POLYLINE]. */
private const val HIGHLIGHT = "_p~iF~ps|U_ulLnnqC"
