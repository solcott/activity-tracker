package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearListEvent
import io.github.solcott.activitytracker.app.gear.GearListPresenter
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActorGearItem
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class GearListPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")

    private fun store(place: String, rkey: String, record: GearItemRecord) {
        fixture.gear.getOrPut(place) { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    @Test
    fun listsItemsFromEveryTierGroupedByTypeWithRetiredLast() = runTest {
        store("repo", "3bike", GearItemRecord("Stumpjumper", GearType.BIKE, created, kind = "mountain"))
        store(Visibility.FOLLOWERS, "3helmet", GearItemRecord("Helmet", GearType.HELMET, created))
        store(Visibility.MUTUALS, "3skis", GearItemRecord("Skis", GearType.SKI, created))
        store(Visibility.SELF, "3shoes", GearItemRecord("Speedgoat 5", GearType.SHOES, created))
        store(Visibility.SELF, "3old", GearItemRecord("Old trainers", GearType.SHOES, created, retiredAt = created))
        val repos = fixture.repos()
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear, repos.gearCollections) }) {
            val lists = awaitUntil { it.content.hasLoaded && !it.content.data.isEmpty }.content.data
            assertEquals(listOf(GearType.BIKE, GearType.SHOES, GearType.SKI, GearType.HELMET), lists.active.map { it.type })
            val visibilities = lists.active.flatMap { it.items }.associate { it.name to it.visibility }
            assertEquals(
                mapOf(
                    "Stumpjumper" to Visibility.PUBLIC,
                    "Speedgoat 5" to Visibility.SELF,
                    "Skis" to Visibility.MUTUALS,
                    "Helmet" to Visibility.FOLLOWERS,
                ),
                visibilities,
            )
            assertEquals("mountain", lists.active.first().items.single().kind)
            assertEquals(listOf("Old trainers"), lists.retired.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noGearIsAnEmptyListThatOffersToAdd() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearListScreen)
        presenterTestOf({ GearListPresenter(navigator, repos.gear, repos.gearCollections) }) {
            val loaded = awaitUntil { it.content.hasLoaded }
            assertTrue(loaded.content.data.isEmpty)
            loaded.eventSink(GearListEvent.Add)
            assertEquals(EditGearScreen(), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnknownTypeIsListedUnderOther() = runTest {
        store(Visibility.SELF, "3uni", GearItemRecord("Unicycle", "unicycle", created, kind = "muni"))
        val repos = fixture.repos()
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear, repos.gearCollections) }) {
            val lists = awaitUntil { it.content.hasLoaded && !it.content.data.isEmpty }.content.data
            val group = lists.active.single()
            assertEquals(GearType.OTHER, group.type)
            assertEquals("Unicycle", group.items.single().name)
            assertEquals(null, group.items.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun served(rkey: String, name: String, type: String, totals: GearTotals) =
        ActorGearItem(GearView(AtUri.of(fixture.did, Nsid.GEAR_ITEM, rkey), name, type), totals, retired = false)

    @Test
    fun rowsCarryTheAppViewsTotalsForTheMixedList() = runTest {
        store("repo", "3bike", GearItemRecord("Tarmac", GearType.BIKE, created))
        store("repo", "3skis", GearItemRecord("Skis", GearType.SKI, created))
        store("repo", "3helmet", GearItemRecord("Helmet", GearType.HELMET, created))
        val bike = GearTotals.ZERO.copy(activities = 80, distance = 3_200_000)
        val skis = GearTotals.ZERO.copy(activities = 14, days = 14, elevationLoss = 41_000)
        val helmet = GearTotals.ZERO.copy(activities = 60, movingTime = 432_000)
        fixture.actorGear =
            listOf(
                served("3bike", "Tarmac", GearType.BIKE, bike),
                served("3skis", "Skis", GearType.SKI, skis),
                served("3helmet", "Helmet", GearType.HELMET, helmet),
            )
        val repos = fixture.repos()
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear, repos.gearCollections) }) {
            val rows = awaitUntil { s ->
                s.content.hasLoaded && s.content.data.active.flatMap { it.items }.all { it.totals != null }
            }
                .content
                .data
                .active
                .flatMap { it.items }
                .associateBy { it.name }
            assertEquals(GearMainTotal.DISTANCE to bike, rows.getValue("Tarmac").let { it.mainTotal to it.totals })
            assertEquals(GearMainTotal.DAYS_AND_VERTICAL to skis, rows.getValue("Skis").let { it.mainTotal to it.totals })
            assertEquals(GearMainTotal.TIME to helmet, rows.getValue("Helmet").let { it.mainTotal to it.totals })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewItemTheAppViewDoesntListYetShowsAtOnceWithNothingCountedButItsStartingUsage() = runTest {
        store("repo", "3new", GearItemRecord("New bike", GearType.BIKE, created, startingDistance = 2_000_000))
        fixture.actorGear = emptyList()
        val repos = fixture.repos()
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear, repos.gearCollections) }) {
            val row = awaitUntil { s ->
                s.content.data.active.firstOrNull()?.items?.single()?.totals != null
            }
                .content
                .data
                .active
                .single()
                .items
                .single()
            assertEquals(GearTotals.ZERO.copy(distance = 2_000_000), row.totals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedTotalsReadStillListsTheItemsWithoutTotals() = runTest {
        store("repo", "3bike", GearItemRecord("Tarmac", GearType.BIKE, created))
        fixture.actorGear = null
        val repos = fixture.repos()
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear, repos.gearCollections) }) {
            val row = awaitUntil { it.content.hasLoaded && !it.content.data.isEmpty }.content.data.active.single().items.single()
            assertEquals(null, row.totals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun openingARowShowsItsGearPage() = runTest {
        store("repo", "3bike", GearItemRecord("Tarmac", GearType.BIKE, created))
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearListScreen)
        presenterTestOf({ GearListPresenter(navigator, repos.gear, repos.gearCollections) }) {
            awaitUntil { it.content.hasLoaded && !it.content.data.isEmpty }.eventSink(GearListEvent.Open("3bike"))
            assertEquals(GearScreen(AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3bike").value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
