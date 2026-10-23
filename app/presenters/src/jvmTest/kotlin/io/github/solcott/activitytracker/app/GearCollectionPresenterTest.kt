package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.EditGearCollectionEvent
import io.github.solcott.activitytracker.app.gear.EditGearCollectionPresenter
import io.github.solcott.activitytracker.app.gear.EditGearCollectionScreen
import io.github.solcott.activitytracker.app.gear.GearCollectionEvent
import io.github.solcott.activitytracker.app.gear.GearCollectionPresenter
import io.github.solcott.activitytracker.app.gear.GearCollectionRow
import io.github.solcott.activitytracker.app.gear.GearCollectionScreen
import io.github.solcott.activitytracker.app.gear.GearListEvent
import io.github.solcott.activitytracker.app.gear.GearListPresenter
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Gear collections' list section, page and form (`gear/gear-collections`: "Managing collections"). */
class GearCollectionPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-23T12:00:00Z")

    private fun uri(rkey: String) = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/$rkey")

    private fun store(place: String, rkey: String, record: GearItemRecord) {
        fixture.gear.getOrPut(place) { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun collection(rkey: String, name: String, vararg members: String) {
        fixture.gearCollections[rkey] =
            AtJson.encodeToJsonElement(GearCollectionRecord.serializer(), GearCollectionRecord(name, created, items = members.map(::uri)))
                as JsonObject
    }

    private fun stored(rkey: String) =
        AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), fixture.gearCollections.getValue(rkey))

    private fun diveKit() {
        store("repo", "3reg", GearItemRecord("Regulator", GearType.DIVE_GEAR, created))
        store(Visibility.SELF, "3suit", GearItemRecord("Wetsuit", GearType.WETSUIT, created, retiredAt = created))
        store("repo", "3fins", GearItemRecord("Fins", GearType.DIVE_GEAR, created))
        collection("3dive", "Dive kit", "3reg", "3suit", "3gone", "3fins")
    }

    @Test
    fun theGearListHasACollectionsSection() = runTest {
        diveKit()
        collection("3winter", "Winter training", "3reg")
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearListScreen)
        presenterTestOf({ GearListPresenter(navigator, repos.gear, repos.gearCollections) }) {
            val lists = awaitUntil { it.content.hasLoaded && it.content.data.collections.isNotEmpty() }
            assertEquals(
                listOf(GearCollectionRow("3dive", "Dive kit", 3), GearCollectionRow("3winter", "Winter training", 1)),
                lists.content.data.collections,
            )
            lists.eventSink(GearListEvent.OpenCollection("3dive"))
            assertEquals(GearCollectionScreen("3dive"), navigator.awaitNextScreen())
            lists.eventSink(GearListEvent.AddCollection)
            assertEquals(EditGearCollectionScreen(), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCollectionListsItsMembersInOrderWithTheRetiredWetsuitMarked() = runTest {
        diveKit()
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearCollectionScreen("3dive"))
        presenterTestOf({ GearCollectionPresenter(GearCollectionScreen("3dive"), navigator, repos.gear, repos.gearCollections) }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.content.data.members.isNotEmpty() }
            val page = loaded.content.data
            assertEquals("Dive kit", page.name)
            assertEquals(listOf("Regulator", "Wetsuit", "Fins"), page.members.map { it.name })
            assertEquals(listOf(false, true, false), page.members.map { it.retired })
            loaded.eventSink(GearCollectionEvent.Open("3suit"))
            assertEquals(GearScreen(uri("3suit").value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingACollectionLeavesItsItems() = runTest {
        diveKit()
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearCollectionScreen("3dive"))
        presenterTestOf({ GearCollectionPresenter(GearCollectionScreen("3dive"), navigator, repos.gear, repos.gearCollections) }) {
            awaitUntil { it.content.hasLoaded }.eventSink(GearCollectionEvent.Delete)
            awaitUntil { it.confirmingDelete }.eventSink(GearCollectionEvent.ConfirmDelete)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.gearCollections.isEmpty())
        assertEquals(3, fixture.gear.values.sumOf { it.size })
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        val repos = fixture.repos()
        val screen = EditGearCollectionScreen()
        presenterTestOf({ EditGearCollectionPresenter(screen, FakeNavigator(screen), repos.gear, repos.gearCollections, Clock.System) }) {
            awaitUntil { it.content.hasLoaded }.eventSink(EditGearCollectionEvent.Save)
            val refused = awaitUntil { it.nameMissing }
            assertFalse(refused.saving)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.gearCollections.isEmpty())
    }

    @Test
    fun raceKitIsCreatedWithItsItemsInTheOrderChosen() = runTest {
        store("repo", "3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        store("repo", "3helmet", GearItemRecord("Aero helmet", GearType.HELMET, created))
        store(Visibility.SELF, "3shoes", GearItemRecord("Race shoes", GearType.SHOES, created))
        val repos = fixture.repos()
        val screen = EditGearCollectionScreen()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ EditGearCollectionPresenter(screen, navigator, repos.gear, repos.gearCollections, Clock.System) }) {
            val form = awaitUntil { it.content.hasLoaded && it.others.size == 3 }
            assertEquals(listOf("Aero helmet", "Race shoes", "Tarmac"), form.others.map { it.name }, "by name")
            form.name.setTextAndPlaceCursorAtEnd("Race kit")
            form.eventSink(EditGearCollectionEvent.Add(persistentListOf("3tarmac", "3helmet", "3shoes")))
            val added = awaitUntil { it.members.size == 3 }
            assertTrue(added.others.isEmpty())
            added.eventSink(EditGearCollectionEvent.Move(from = 2, to = 1))
            awaitUntil { it.members.map { m -> m.rkey } == listOf("3tarmac", "3shoes", "3helmet") }.eventSink(EditGearCollectionEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = stored(fixture.gearCollections.keys.single())
        assertEquals("Race kit", saved.name)
        assertEquals(listOf(uri("3tarmac"), uri("3shoes"), uri("3helmet")), saved.items)
    }

    @Test
    fun editingRenamesRemovesAndDropsDeletedMembers() = runTest {
        diveKit()
        val repos = fixture.repos()
        val screen = EditGearCollectionScreen("3dive")
        val navigator = FakeNavigator(screen)
        presenterTestOf({ EditGearCollectionPresenter(screen, navigator, repos.gear, repos.gearCollections, Clock.System) }) {
            val form = awaitUntil { it.content.hasLoaded && it.members.isNotEmpty() }
            assertEquals("Dive kit", form.name.text.toString())
            assertEquals(listOf("3reg", "3suit", "3fins"), form.members.map { it.rkey })
            form.name.setTextAndPlaceCursorAtEnd("Cold water kit")
            form.eventSink(EditGearCollectionEvent.Remove("3fins"))
            awaitUntil { it.members.size == 2 }.eventSink(EditGearCollectionEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = stored("3dive")
        assertEquals("Cold water kit", saved.name)
        assertEquals(listOf(uri("3reg"), uri("3suit")), saved.items)
        assertNull(saved.notes)
    }
}
