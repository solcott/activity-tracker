package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.EditGearEvent
import io.github.solcott.activitytracker.app.gear.EditGearPresenter
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.EditGearState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class EditGearPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val bike = GearItemRecord("Stumpjumper", GearType.BIKE, created, kind = "mountain", brand = "Specialized")

    private fun store(place: String, rkey: String, record: GearItemRecord) {
        fixture.gear.getOrPut(place) { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun stored(place: String, rkey: String): GearItemRecord? =
        fixture.gear[place]?.get(rkey)?.let { AtJson.decodeFromJsonElement(GearItemRecord.serializer(), it) }

    private suspend fun test(
        screen: EditGearScreen,
        navigator: FakeNavigator = FakeNavigator(screen),
        block: suspend ReceiveTurbine<EditGearState>.() -> Unit,
    ) {
        val repos = fixture.repos()
        presenterTestOf(
            { EditGearPresenter(screen, navigator, repos.gear, repos.gearDefaults, repos.profiles, Clock.System) },
            block = block,
        )
    }

    @Test
    fun aNewItemStartsAtOnlyMeAndSavesThere() = runTest {
        val navigator = FakeNavigator(EditGearScreen())
        test(EditGearScreen(), navigator) {
            val form = awaitUntil { it.content.hasLoaded }
            assertEquals(Visibility.SELF, form.visibility)
            assertTrue(form.isNew)
            form.name.setTextAndPlaceCursorAtEnd("Old trainers")
            form.eventSink(EditGearEvent.TypeChanged(GearType.SHOES))
            awaitUntil { it.type == GearType.SHOES }.eventSink(EditGearEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = fixture.gear.getValue(Visibility.SELF).values.single()
        val record = AtJson.decodeFromJsonElement(GearItemRecord.serializer(), saved)
        assertEquals("Old trainers", record.name)
        assertEquals(GearType.SHOES, record.type)
        assertNull(record.brand)
        assertNull(fixture.gear[pdsRepo])
    }

    @Test
    fun aDoubleTapSavesOnce() = runTest {
        val navigator = FakeNavigator(EditGearScreen())
        test(EditGearScreen(), navigator) {
            val form = awaitUntil { it.content.hasLoaded }
            form.name.setTextAndPlaceCursorAtEnd("Skis")
            val gate = CompletableDeferred<Unit>()
            fixture.gate = gate
            form.eventSink(EditGearEvent.Save)
            awaitUntil { it.saving }.eventSink(EditGearEvent.Save)
            gate.complete(Unit)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.gear.getValue(Visibility.SELF).size)
        assertEquals(1, fixture.spacePuts.size)
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        test(EditGearScreen()) {
            val form = awaitUntil { it.content.hasLoaded }
            form.name.setTextAndPlaceCursorAtEnd("   ")
            form.eventSink(EditGearEvent.Save)
            val refused = awaitUntil { it.nameMissing }
            assertNull(refused.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.gear.isEmpty())
    }

    @Test
    fun choosingRetireInTheDeleteConfirmationRetires() = runTest {
        store(pdsRepo, "3bike", bike)
        val navigator = FakeNavigator(EditGearScreen("3bike"))
        test(EditGearScreen("3bike"), navigator) {
            val form = awaitUntil { it.content.hasLoaded && it.name.text.isNotEmpty() }
            assertEquals("Stumpjumper", form.name.text.toString())
            assertEquals("mountain", form.kind)
            assertEquals(Visibility.PUBLIC, form.visibility)
            form.eventSink(EditGearEvent.Delete)
            awaitUntil { it.confirmingDelete }.eventSink(EditGearEvent.Retire)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val retired = assertNotNull(stored(pdsRepo, "3bike"))
        assertNotNull(retired.retiredAt)
        assertEquals("Specialized", retired.brand)
    }

    @Test
    fun aConfirmedDeleteRemovesTheItem() = runTest {
        store(Visibility.FOLLOWERS, "3helmet", GearItemRecord("Helmet", GearType.HELMET, created))
        val navigator = FakeNavigator(EditGearScreen("3helmet"))
        test(EditGearScreen("3helmet"), navigator) {
            val form = awaitUntil { it.content.hasLoaded && it.name.text.isNotEmpty() }
            form.eventSink(EditGearEvent.Delete)
            awaitUntil { it.confirmingDelete }.eventSink(EditGearEvent.ConfirmDelete)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(stored(Visibility.FOLLOWERS, "3helmet"))
    }

    @Test
    fun anOlderSessionIsAskedToSignInAgainAndKeepsTheForm() = runTest {
        fixture.scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}"
        val navigator = FakeNavigator(EditGearScreen())
        test(EditGearScreen(), navigator) {
            val form = awaitUntil { it.content.hasLoaded }
            form.name.setTextAndPlaceCursorAtEnd("Speedgoat 5")
            form.eventSink(EditGearEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.GearSignInAgain, failed.error?.kind)
            assertEquals("Speedgoat 5", failed.name.text.toString())

            failed.eventSink(EditGearEvent.VisibilityChanged(Visibility.PUBLIC))
            awaitUntil { it.visibility == Visibility.PUBLIC }.eventSink(EditGearEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Speedgoat 5", fixture.gear.getValue(pdsRepo).values.single()["name"].toString().trim('"'))
    }

    @Test
    fun unretiringClearsTheDate() = runTest {
        store(Visibility.SELF, "3shoes", GearItemRecord("Speedgoat 5", GearType.SHOES, created, retiredAt = created))
        val navigator = FakeNavigator(EditGearScreen("3shoes"))
        test(EditGearScreen("3shoes"), navigator) {
            val form = awaitUntil { it.content.hasLoaded && it.retiredOn != null }
            form.eventSink(EditGearEvent.Unretire)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(assertNotNull(stored(Visibility.SELF, "3shoes")).retiredAt)
    }

    private companion object {
        const val pdsRepo = "repo"
    }

    @Test
    fun anItemBecomesADefaultForTheTypesTicked() = runTest {
        store(pdsRepo, "3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        val navigator = FakeNavigator(EditGearScreen("3tarmac"))
        val ride = ActivityType(Sport.RIDE)
        test(EditGearScreen("3tarmac"), navigator) {
            val form = awaitUntil { it.content.hasLoaded }
            assertEquals(emptyList(), form.defaultFor)
            form.eventSink(EditGearEvent.ToggleDefault(ride))
            awaitUntil { it.defaultFor == listOf(ride) }.eventSink(EditGearEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = AtJson.decodeFromString(GearAutoAssignRecord.serializer(), fixture.gearAutoAssign!!)
        val tarmac = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/3tarmac")
        assertEquals(listOf(GearSportDefault(Sport.RIDE, listOf(tarmac))), saved.sports)

        // Opened again, the form shows it.
        test(EditGearScreen("3tarmac")) {
            assertEquals(listOf(ride), awaitUntil { it.content.hasLoaded && it.defaultFor.isNotEmpty() }.defaultFor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOlderSessionCantSaveDefaults() = runTest {
        fixture.scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.GEAR_ITEM}"
        store(pdsRepo, "3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        test(EditGearScreen("3tarmac")) {
            val form = awaitUntil { it.content.hasLoaded }
            form.eventSink(EditGearEvent.ToggleDefault(ActivityType(Sport.RIDE)))
            awaitUntil { it.defaultFor.isNotEmpty() }.eventSink(EditGearEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.GearSignInAgain, failed.error?.kind)
            assertEquals(listOf(ActivityType(Sport.RIDE)), failed.defaultFor, "the form keeps the choice")
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.gearAutoAssign)
    }
}
