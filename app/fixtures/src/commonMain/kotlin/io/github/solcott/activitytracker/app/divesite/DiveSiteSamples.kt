package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.UiError
import kotlin.time.Duration.Companion.days
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewerState
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSite
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteConditions
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteRecord
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeRegularEntry
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.data.divesites.RegularsMeta
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object DiveSiteSamples {
    val site: DiveSiteView = fakeDiveSite()

    val dives: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity(
                "3kdive1",
                fakeProfileBasic(Fixed.BOB),
                fakeActivityRecord(Sport.DIVE, "Blue Hole wall", distance = null, polyline = null),
            ),
            fakeActivity("3kdive2", record = fakeActivityRecord(Sport.DIVE, "Arch swim-through", distance = null, polyline = null)),
        )

    private fun state(content: ContentState<DiveSiteView?>, site: DiveSiteView? = content.data) =
        DiveSiteState(
            content = content,
            outline = site?.record?.let { siteOutline(it.lat, it.lng, it.radius) },
            conditions = site?.let { fakeDiveSiteConditions() },
            regulars =
                site?.let {
                    RegularsSummary(
                        fakeRegularEntry(rank = 1, athlete = fakeProfileBasic(Fixed.BOB), visits = 31),
                        fakeRegularEntry(rank = 4, athlete = fakeProfileBasic(), visits = 6),
                    )
                },
            dives = if (site == null) PagedStates.loading() else PagedStates.end(dives, Unit),
            viewerDid = Fixed.ALICE.value,
        )

    val loading: DiveSiteState
        get() = DiveSiteState(content = ContentStates.loading(null))

    val failed: DiveSiteState
        get() = DiveSiteState(content = ContentStates.failed(null))

    val loaded: DiveSiteState
        get() = state(ContentStates.loaded(site))

    val refreshing: DiveSiteState
        get() = state(ContentStates.refreshing(site))

    val refreshFailed: DiveSiteState
        get() = state(ContentStates.refreshFailed(site))

    /** An OpenStreetMap site still proposed, with no conditions, no Regulars and no dives the viewer may see. */
    val proposed: DiveSiteState
        get() {
            val osm =
                fakeDiveSite(record = fakeDiveSiteRecord("SS Thistlegorm", source = SegmentSource.OSM, license = "ODbL-1.0", radius = 150))
            return DiveSiteState(
                content = ContentStates.loaded(osm.copy(status = SegmentStatus.PROPOSED)),
                outline = osm.record.let { siteOutline(it.lat, it.lng, it.radius) },
                dives = PagedStates.end(emptyList(), Unit),
                viewerDid = Fixed.ALICE.value,
            )
        }

    val all: Map<String, DiveSiteState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
                PROPOSED to proposed,
            )

    const val PROPOSED = "proposed"

    /**
     * A site the community renamed (dive-sites/site-names): its history, Bob's open proposal with the viewer's vote for it, and Alice's
     * own; Alice may vote.
     */
    val renamed: DiveSiteState
        get() {
            val alice = fakeProfileBasic(Fixed.ALICE)
            val bob = fakeProfileBasic(Fixed.BOB)
            val named =
                site.copy(
                    record = site.record.copy(name = "Ras Mohammed"),
                    name = "Shark Alley",
                    nameHistory =
                        listOf(SegmentNameAdoption("Shark Alley", Fixed.NOW - 40.days, up = 1_204, down = 3, proposer = bob)),
                    nameProposals =
                        listOf(
                            SegmentNameProposalView(
                                uri = Fixed.uri(bob.did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3ksiteprop1"),
                                cid = Fixed.cid("3ksiteprop1"),
                                name = "The Wall",
                                proposer = bob,
                                openedAt = Fixed.NOW - 3.days,
                                up = 4,
                                down = 1,
                                viewerVote =
                                    SegmentNameViewerVote(
                                        Fixed.uri(alice.did, Nsid.FEED_SEGMENT_NAME_VOTE, "3ksitevote1"),
                                        SegmentNameVoteDirection.UP,
                                    ),
                            ),
                            SegmentNameProposalView(
                                uri = Fixed.uri(alice.did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3ksiteprop2"),
                                cid = Fixed.cid("3ksiteprop2"),
                                name = "Coral Gardens",
                                proposer = alice,
                                openedAt = Fixed.NOW - 1.days,
                                up = 2,
                                down = 0,
                            ),
                        ),
                    viewer = DiveSiteViewerState(canProposeName = false, canVoteOnNames = true),
                )
            return state(ContentStates.loaded(named)).copy(names = PlaceNameFields(viewerDid = alice.did.value))
        }

    /** [renamed] for Carol, who may propose, whose session can't vote, and whose last vote failed. */
    val votingUnavailable: DiveSiteState
        get() =
            renamed.let {
                val named = checkNotNull(it.site)
                it.copy(
                    content = ContentStates.loaded(named.copy(viewer = named.viewer?.copy(canProposeName = true))),
                    names =
                        PlaceNameFields(
                            canVoteHere = false,
                            viewerDid = Fixed.CAROL.value,
                            error = UiError(UiError.Kind.VoteSegmentName),
                        ),
                    viewerDid = Fixed.CAROL.value,
                )
            }

    /** A duplicate of an earlier site, archived conditions aside: its status, help and Open the original. */
    val duplicate: DiveSiteState
        get() {
            val copy =
                fakeDiveSite(rkey = "3ksite2", status = SegmentStatus.DUPLICATE)
                    .copy(duplicateOf = Fixed.uri(Fixed.ALICE, Nsid.FEED_DIVE_SITE, "3ksite1"))
            return DiveSiteState(
                content = ContentStates.loaded(copy),
                outline = copy.record.let { siteOutline(it.lat, it.lng, it.radius) },
                dives = PagedStates.end(emptyList(), Unit),
                viewerDid = Fixed.ALICE.value,
            )
        }

    /** Kept apart from [all], whose keys are the content-loading set. */
    val variants: Map<String, DiveSiteState>
        get() = mapOf("renamed" to renamed, "votingUnavailable" to votingUnavailable, "duplicate" to duplicate)
}

object DiveSiteRegularsSamples {
    val entries: ImmutableList<DiveSiteRegularEntry> =
        persistentListOf(
            fakeRegularEntry(rank = 1, athlete = fakeProfileBasic(Fixed.BOB), visits = 31),
            fakeRegularEntry(rank = 2, athlete = fakeProfileBasic(Fixed.CAROL), visits = 17),
        )
    private val meta = RegularsMeta(viewer = null)

    val all: Map<String, DiveSiteRegularsState>
        get() = pagedSamples(entries, meta) { DiveSiteRegularsState(DiveSiteSamples.site.currentName, entries = it) }

    /** Last year's board with the viewer's own place at 12 (`lastYear`). Kept apart from [all], whose keys are the content-loading set. */
    val variants: Map<String, DiveSiteRegularsState>
        get() =
            mapOf(
                "lastYear" to
                    DiveSiteRegularsState(
                        DiveSiteSamples.site.currentName,
                        period = RegularsPeriod.LAST_YEAR,
                        entries = PagedStates.end(entries, meta),
                        viewerEntry = fakeRegularEntry(rank = 12, athlete = fakeProfileBasic(Fixed.ALICE), visits = 4),
                    ),
            )
}

object CreateDiveSiteSamples {
    private fun loadedState(name: String = "Ras Mohammed") =
        CreateDiveSiteState(
            content = ContentStates.loaded(Unit),
            outline = DiveSiteSamples.site.record.let { siteOutline(it.lat, it.lng, it.radius) },
            name = textFieldStateOf(name),
            named = name.isNotBlank(),
        )

    val all: Map<String, CreateDiveSiteState>
        get() =
            mapOf(
                SampleKind.LOADING to CreateDiveSiteState(content = ContentStates.loading(Unit)),
                SampleKind.FAILED to
                    CreateDiveSiteState(content = ContentStates.failed(Unit), loadErrorKind = UiError.Kind.LoadDivePosition),
                SampleKind.LOADED to loadedState(),
                SampleKind.INVALID to loadedState(name = ""),
                INSIDE_ZONE to
                    CreateDiveSiteState(content = ContentStates.loaded(Unit), unavailable = CreateDiveSiteUnavailable.InsideZone),
                DUPLICATE to loadedState().copy(duplicates = persistentListOf(fakeDiveSite(name = "Ras Mohammed Shark Reef"))),
            )

    const val INSIDE_ZONE = "insideZone"
    const val DUPLICATE = "duplicate"
}
