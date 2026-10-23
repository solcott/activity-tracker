package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearListEvent
import io.github.solcott.activitytracker.app.gear.GearListPresenter
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
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
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear) }) {
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
        presenterTestOf({ GearListPresenter(navigator, repos.gear) }) {
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
        presenterTestOf({ GearListPresenter(FakeNavigator(GearListScreen), repos.gear) }) {
            val lists = awaitUntil { it.content.hasLoaded && !it.content.data.isEmpty }.content.data
            val group = lists.active.single()
            assertEquals(GearType.OTHER, group.type)
            assertEquals("Unicycle", group.items.single().name)
            assertEquals(null, group.items.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
