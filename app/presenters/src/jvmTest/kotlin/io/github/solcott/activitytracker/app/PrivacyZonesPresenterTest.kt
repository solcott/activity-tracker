package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.PrivacyZonesEvent
import io.github.solcott.activitytracker.app.settings.PrivacyZonesPresenter
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesState
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class PrivacyZonesPresenterTest {
    private val fixture = SessionTestFixture()
    private val home = PrivacyZone(latitudeE7 = 515_000_000, longitudeE7 = -1_200_000, radiusMeters = 400, label = "Home")

    private fun withZones(vararg zones: PrivacyZone) {
        fixture.privacyZones = AtJson.encodeToString(PrivacyZonesRecord.serializer(), PrivacyZonesRecord("3l2aaaaaaaa22", zones.toList()))
    }

    private fun savedZones(): PrivacyZonesRecord? =
        fixture.spacePuts
            .lastOrNull { (_, record) -> record["\$type"].toString().contains(Nsid.ACTOR_PRIVACY_ZONES) }
            ?.let { (_, r) -> AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), r) }

    private suspend fun test(
        tracker: LocationTracker = FakeLocationTracker(),
        block: suspend ReceiveTurbine<PrivacyZonesState>.() -> Unit,
    ) {
        val repos = fixture.repos()
        presenterTestOf({ PrivacyZonesPresenter(FakeNavigator(PrivacyZonesScreen), repos.privacy, tracker) }, block = block)
    }

    @Test
    fun showsTheSavedZonesAndSavesARadiusChangeOnce() = runTest {
        withZones(home)
        test {
            val loaded = awaitUntil { it.zones != null }
            assertEquals<List<PrivacyZone>?>(listOf(home), loaded.zones)
            assertTrue(loaded.canEdit)

            // A double tap: the second is dropped while the first saves.
            loaded.eventSink(PrivacyZonesEvent.RadiusChanged(0, 800))
            loaded.eventSink(PrivacyZonesEvent.RadiusChanged(0, 800))
            val saved = awaitUntil { it.zones?.single()?.radiusMeters == 800 && !it.saving }
            assertNull(saved.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(800, savedZones()?.zones?.single()?.radiusMeters)
        assertEquals(1, fixture.spacePuts.count { (_, r) -> r["\$type"].toString().contains(Nsid.ACTOR_PRIVACY_ZONES) })
    }

    @Test
    fun removingAZoneAndHidingEndsSave() = runTest {
        withZones(home)
        test {
            awaitUntil { it.zones != null }.eventSink(PrivacyZonesEvent.Remove(0))
            awaitUntil { it.zones?.isEmpty() == true && !it.saving }.eventSink(PrivacyZonesEvent.HideEndsChanged(400))
            awaitUntil { it.hideEndsMeters == 400 && !it.saving }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, savedZones()?.zones)
        assertEquals(400, savedZones()?.hideEndsMeters)
    }

    @Test
    fun aFailedSaveKeepsTheZonesAndSaysSo() = runTest {
        withZones(home)
        fixture.failWrites = true
        test {
            awaitUntil { it.zones != null }.eventSink(PrivacyZonesEvent.Remove(0))
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SavePrivacyZones, failed.error?.kind)
            assertEquals<List<PrivacyZone>?>(listOf(home), failed.zones)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun zonesThatCantBeReadCantBeChanged() = runTest {
        fixture.failZoneReads = true
        test {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertIs<DataError.Api>(failed.content.errorOrNull)
            assertNull(failed.error, "a load failure isn't a change's")
            assertNull(failed.zones)
            assertFalse(failed.canEdit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun zonesUnreadableOfflineSayWhyAndRetryShowsThem() = runTest {
        withZones(home)
        fixture.offline = true
        test {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertFalse(failed.canEdit)

            fixture.offline = false
            failed.eventSink(PrivacyZonesEvent.Retry)
            awaitUntil { it.content.isLoading }
            val loaded = awaitUntil { it.zones != null }
            assertTrue(loaded.content.hasLoaded)
            assertEquals<List<PrivacyZone>?>(listOf(home), loaded.zones)
            assertTrue(loaded.canEdit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionThatCantSaveZonesAsksToSignInAgain() = runTest {
        fixture.scope = "atproto transition:generic"
        test {
            val loaded = awaitUntil { it.zones != null }
            assertTrue(loaded.needsSignIn)
            assertNull(loaded.error)
            assertFalse(loaded.canEdit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aZoneIsAddedAtTheCurrentLocationOffsetFromIt() = runTest {
        val here = LocationSample(51.5, -0.12, null, speed = null, accuracy = 5.0, time = Instant.parse("2026-10-15T08:00:00Z"))
        test(FakeLocationTracker(listOf(here))) {
            val loaded = awaitUntil { it.zones != null }
            assertTrue(loaded.canUseLocation)
            loaded.label.edit { replace(0, length, "Home") }
            loaded.eventSink(PrivacyZonesEvent.AddAtCurrentLocation)
            val added = awaitUntil { it.zones?.size == 1 && !it.locating }
            assertEquals("Home", added.zones!!.single().label)
            cancelAndIgnoreRemainingEvents()
        }
        val zone = savedZones()!!.zones!!.single()
        assertEquals(400, zone.radiusMeters)
        assertTrue(TrackMath.distanceMeters(51.5, -0.12, zone.latitudeE7 / 1e7, zone.longitudeE7 / 1e7) <= 200.0)
    }

    @Test
    fun desktopAndWebOfferNoCurrentLocation() = runTest {
        test(UnsupportedLocationTracker()) {
            assertFalse(awaitUntil { it.zones != null }.canUseLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
