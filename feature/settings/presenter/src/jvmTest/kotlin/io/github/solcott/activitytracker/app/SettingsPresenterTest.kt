package io.github.solcott.activitytracker.app

import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.moderation.ModerationScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsScreen
import io.github.solcott.activitytracker.app.settings.SettingsEvent
import io.github.solcott.activitytracker.app.settings.SettingsPresenter
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SettingsState
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/** Settings' list (ui/settings-layout): units, the rows' summaries, opening pages and moderation. */
class SettingsPresenterTest {
    private val fixture = SessionTestFixture()

    private suspend fun SessionTestFixture.settings(
        navigator: FakeNavigator = FakeNavigator(SettingsScreen),
        moderation: ModerationRepository? = null,
        block: suspend app.cash.turbine.ReceiveTurbine<SettingsState>.() -> Unit,
    ) {
        val repos = repos()
        val forModeration = moderation ?: moderation()
        presenterTestOf(
            { SettingsPresenter(navigator, repos.clients, repos.units, repos.defaults, repos.privacy, forModeration, repos.segmentPrefs) },
        ) {
            block()
        }
    }

    @Test
    fun choosingUnitsSavesThemToTheProfile() = runTest {
        fixture.settings {
            val initial = awaitItem()
            assertEquals(UnitSystem.Metric, initial.units)
            assertNull(initial.error)

            initial.eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            val saved = awaitUntil { it.units == UnitSystem.Imperial && !it.saving }
            assertNull(saved.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("imperial", fixture.putRecords.single()["units"]?.jsonPrimitive?.content)
    }

    @Test
    fun failedSaveRevertsAndShowsError() = runTest {
        fixture.failWrites = true
        fixture.settings {
            awaitItem().eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            // The revert comes through the units flow, which can land a frame after the error.
            val failed = awaitUntil { it.error != null && !it.saving && it.units == UnitSystem.Metric }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backPops() = runTest {
        val navigator = FakeNavigator(SettingsScreen)
        fixture.settings(navigator) {
            awaitItem().eventSink(SettingsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backWithAPageOpenLeavesSettings() = runTest {
        // A wide window: Gear shows beside the list, which Settings was opened onto from the training zones.
        val navigator = FakeNavigator(TrainingZonesScreen, SettingsScreen, GearListScreen)
        fixture.settings(navigator) {
            awaitItem().eventSink(SettingsEvent.Back)
            assertEquals(listOf<Screen>(TrainingZonesScreen), navigator.peekBackStack())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rowsSummarizeTheirPagesOnceRead() = runTest {
        fixture.activityDefaults = """{"visibility":"followers"}"""
        fixture.privacyZones =
            AtJson.encodeToString(
                PrivacyZonesRecord.serializer(),
                PrivacyZonesRecord(
                    "3l2aaaaaaaa22",
                    listOf(PrivacyZone(400_150_000, -1_052_700_000, 400), PrivacyZone(400_250_000, -1_052_800_000, 400)),
                    hideEndsMeters = 200,
                ),
            )
        fixture.segmentPrefs = """{"matching":true,"updatedAt":"2026-10-15T12:00:00Z"}"""
        fixture.settings {
            val first = awaitItem()
            assertNull(first.segmentMatching, "unread: no summary")
            val read = awaitUntil { it.defaultActivityVisibility != null && it.privacyZoneCount != null && it.segmentMatching != null }
            assertEquals(Visibility.FOLLOWERS, read.defaultActivityVisibility)
            assertEquals(2, read.privacyZoneCount)
            assertEquals(true, read.hidesRouteEnds)
            assertEquals(true, read.segmentMatching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rowsOpenTheirPages() = runTest {
        val navigator = FakeNavigator(SettingsScreen)
        fixture.settings(navigator) {
            val state = awaitItem()
            state.eventSink(SettingsEvent.Open(TrainingZonesScreen))
            assertEquals(TrainingZonesScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRowReplacesThePageSettingsHasOpen() = runTest {
        // A wide window: Privacy zones, and a page under it, show beside the list.
        val navigator = FakeNavigator(SettingsScreen, PrivacyZonesScreen, SegmentsAndGroupsScreen)
        fixture.settings(navigator) {
            awaitItem().eventSink(SettingsEvent.Open(GearListScreen))
            assertEquals(GearListScreen, navigator.awaitNextScreen())
            assertEquals(listOf(GearListScreen, SettingsScreen), navigator.peekBackStack())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsOffersModerationOnlyToModerators() = runTest {
        val navigator = FakeNavigator(SettingsScreen, PrivacyZonesScreen)
        fixture.settings(navigator, moderationAs("moderator")) {
            val offered = awaitUntil { it.canModerate }
            offered.eventSink(SettingsEvent.OpenModeration)
            assertEquals(ModerationScreen, navigator.awaitNextScreen())
            assertEquals(listOf(ModerationScreen, SettingsScreen), navigator.peekBackStack(), "never stacked on a page")
            cancelAndIgnoreRemainingEvents()
        }
        val other = FakeNavigator(SettingsScreen)
        fixture.settings(other, moderationAs("triage")) {
            val state = awaitItem()
            state.eventSink(SettingsEvent.OpenModeration)
            assertEquals(false, state.canModerate)
            other.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A moderation repository whose AppView answers `getRole` with [role]. */
    private suspend fun moderationAs(role: String): ModerationRepository {
        val http =
            HttpClient(
                MockEngine { request ->
                    val json = headersOf("Content-Type", "application/json")
                    when (request.url.encodedPath) {
                        "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                        "/xrpc/${Nsid.MODERATION_GET_ROLE}" -> respond("""{"role":"$role"}""", headers = json)
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                },
            ) {
                expectSuccess = false
            }
        return ModerationRepository(signedInTestSession(http, labelerDid = Did("did:plc:labeler")).clients)
    }

    @Test
    fun theAccountRowShowsTheHandleAndWhetherToSignInAgain() = runTest {
        fixture.settings {
            val current = awaitItem()
            assertEquals("alice.test", current.handle)
            assertEquals(false, current.needsSignIn)
            cancelAndIgnoreRemainingEvents()
        }
        fixture.scope = "atproto transition:generic"
        fixture.settings {
            assertEquals(true, awaitItem().needsSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
