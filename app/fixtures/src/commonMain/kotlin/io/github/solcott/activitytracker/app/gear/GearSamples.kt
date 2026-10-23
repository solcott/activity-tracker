package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object GearListSamples {
    private fun totals(distance: Long = 0, movingTime: Long = 0, days: Long = 0, vertical: Long = 0, activities: Long = 1) =
        GearTotals(activities, movingTime, distance, elevationGain = 0, elevationLoss = vertical, days = days)

    /** The spec's mixed list: a bike with 3,200 km, skis with 14 days and 41,000 m of vertical, a helmet with 120 hours, new shoes. */
    private val lists =
        GearLists(
            active =
                persistentListOf(
                    GearGroup(
                        GearType.BIKE,
                        persistentListOf(
                            GearRow(
                                "3kgear1",
                                "Gravel bike",
                                GearType.BIKE,
                                null,
                                Visibility.PUBLIC,
                                null,
                                totals = totals(distance = 3_200_000),
                            )
                        ),
                    ),
                    GearGroup(
                        GearType.SHOES,
                        persistentListOf(
                            GearRow("3kgear2", "Trail shoes", GearType.SHOES, null, Visibility.SELF, null, totals = totals(activities = 0))
                        ),
                    ),
                    GearGroup(
                        GearType.SKI,
                        persistentListOf(
                            GearRow(
                                "3kgear4",
                                "Powder skis",
                                GearType.SKI,
                                null,
                                Visibility.PUBLIC,
                                null,
                                totals = totals(days = 14, vertical = 41_000),
                            )
                        ),
                    ),
                    GearGroup(
                        GearType.HELMET,
                        persistentListOf(
                            GearRow(
                                "3kgear5",
                                "Helmet",
                                GearType.HELMET,
                                null,
                                Visibility.FOLLOWERS,
                                null,
                                totals = totals(movingTime = 432_000),
                            )
                        ),
                    ),
                ),
            retired = persistentListOf(GearRow("3kgear3", "Old road bike", GearType.BIKE, null, Visibility.PUBLIC, null)),
        )

    val all: Map<String, GearListState>
        get() =
            contentSamples(GearLists(), lists) { GearListState(it, eventSink = {}) } +
                (SampleKind.EMPTY to GearListState(ContentStates.loaded(GearLists()), eventSink = {}))
}

object EditGearSamples {
    private fun state(content: ContentState<Unit>, name: String = "Gravel bike", isNew: Boolean = false) =
        EditGearState(
            content = content,
            isNew = isNew,
            name = TextFieldState(name),
            brand = TextFieldState("Rivendell"),
            model = TextFieldState("Appaloosa"),
            weight = TextFieldState("11.2"),
            startingDistance = TextFieldState("2000"),
            startingTime = TextFieldState(),
            units = UnitSystem.Metric,
            notes = TextFieldState(),
            type = GearType.BIKE,
            types = GearType.all.toImmutableList(),
            kind = null,
            kinds = GearType.kinds(GearType.BIKE).toImmutableList(),
            sports = persistentListOf(Sport.RIDE),
            visibility = Visibility.PUBLIC,
            acquiredOn = null,
            retiredOn = null,
            photo = null,
            nameMissing = name.isBlank(),
            saving = false,
            confirmingDelete = false,
            approvesFollowers = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, EditGearState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), name = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), name = ""),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
                SampleKind.INVALID to state(ContentStates.loaded(Unit), name = ""),
                "new" to state(ContentStates.loaded(Unit), name = "", isNew = true).copy(nameMissing = false),
            )
}

object GearSamples {
    val uri = Fixed.uri(Fixed.ALICE, Nsid.GEAR_ITEM, "3kgear1")

    private val totals =
        GearTotals(activities = 2, movingTime = 7_200, distance = 64_800, elevationGain = 820, elevationLoss = 800, days = 2)

    /** Alice's gravel bike, bought used with 2,000 km on it, over two rides: a road ride in 2025 and a gravel ride in 2026. */
    val page =
        GearPage(
            gear = GearView(uri, "Gravel bike", GearType.BIKE, kind = "gravel", brand = "Rivendell", model = "Appaloosa"),
            usage =
                GearUsage(
                    totals = totals.copy(distance = totals.distance + 2_000_000),
                    bySport =
                        listOf(
                            GearSportTotals(Sport.RIDE, totals.copy(activities = 1, distance = 32_400, days = 1), subSport = "gravel"),
                            GearSportTotals(Sport.RIDE, totals.copy(activities = 1, distance = 32_400, days = 1), subSport = "road"),
                        ),
                    byYear =
                        listOf(
                            GearYearTotals(2026, totals.copy(activities = 1, distance = 32_400, days = 1)),
                            GearYearTotals(2025, totals.copy(activities = 1, distance = 32_400, days = 1)),
                        ),
                    starting = GearStarting(distance = 2_000_000),
                ),
            retired = false,
            isOwner = true,
            visibility = Visibility.PUBLIC,
        )

    /** [page] without breakdowns, so a page of activities and its footer fit on screen. */
    private val compact: GearPage
        get() = page.copy(usage = page.usage.copy(bySport = page.usage.bySport.take(1), byYear = page.usage.byYear.take(1)))

    val activities: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity("3kride2", record = fakeActivityRecord(title = "Gravel loop")),
            fakeActivity("3kride1", record = fakeActivityRecord(title = "Morning Ride")),
        )

    private fun state(
        content: ContentState<GearPage?>,
        activities: PagedState<ActivityView, Unit> = PagedStates.end(this.activities, Unit),
    ) = GearState(content = content, activities = activities, viewerDid = Fixed.ALICE.value)

    val all: Map<String, GearState>
        get() =
            contentSamples<GearPage?, GearState>(null, page) {
                state(it, if (it.data == null) PagedStates.loading() else PagedStates.end(activities, Unit))
            } +
                (SampleKind.EMPTY to
                    state(
                        ContentStates.loaded(page.copy(usage = GearUsage(GearTotals.ZERO, emptyList(), emptyList()))),
                        PagedStates.end(emptyList(), Unit),
                    ))

    /**
     * The next page of activities loading, below a page without breakdowns. Not in [all]: like the activity page's comments, the list
     * starts below the fold, where the conformance test can't see its footer.
     */
    val loadingMore: GearState
        get() = state(ContentStates.loaded(compact), PagedStates.loadingMore(activities.take(1), Unit))

    /** A follower's view of the bike: no Edit, no visibility. */
    val follower: GearState
        get() = state(ContentStates.loaded(page.copy(isOwner = false, visibility = null)))

    /** An item the viewer may not see. */
    val notFound: GearState
        get() = GearState(notFound = true, activities = PagedStates.loading(), viewerDid = Fixed.BOB.value)
}
