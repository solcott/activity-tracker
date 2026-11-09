package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteEvent
import io.github.solcott.activitytracker.app.divesite.CreateDiveSitePresenter
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteScreen
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteState
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteUnavailable
import io.github.solcott.activitytracker.app.divesite.DiveSiteScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Creating a dive site from one's own dive (dive-sites/site-creation). */
class CreateDiveSitePresenterTest : ActivityTestBase() {
    private val start = Instant.parse("2026-09-30T10:00:00Z")

    /** A shore dive's entry and exit fixes, 200 m apart, near Dahab. */
    private val fixes =
        listOf(
            LocationSample(28.5720, 34.5380, null, speed = null, accuracy = 5.0, time = start),
            LocationSample(28.5738, 34.5380, null, speed = null, accuracy = 5.0, time = start + 45.minutes),
        )

    private val createScreen = CreateDiveSiteScreen(activityUri)

    private val existing =
        """{"uri":"at://did:plc:carol/${Nsid.FEED_DIVE_SITE}/3kshark","cid":"bafysite","creator":$carol,""" +
            """"record":{"name":"Shark Reef","lat":285720000,"lng":345380000,"radius":100,"source":"athlete","license":"CC0-1.0",""" +
            """"createdAt":"2026-09-01T00:00:00Z"},"status":"active"}"""

    private suspend fun create(
        navigator: FakeNavigator = FakeNavigator(createScreen),
        block: suspend app.cash.turbine.ReceiveTurbine<CreateDiveSiteState>.(Repos) -> Unit,
    ) {
        val r = repos()
        presenterTestOf(
            { CreateDiveSitePresenter(createScreen, navigator, r.feed, r.activities, r.privacy, r.diveSites, r.sitePublisher) },
        ) {
            block(r)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CreateDiveSiteState>.awaitReady() = awaitUntil { it.outline != null }

    private suspend fun app.cash.turbine.ReceiveTurbine<CreateDiveSiteState>.awaitUnavailable() = awaitUntil {
        it.unavailable != null
    }
        .unavailable

    private suspend fun Repos.queuedSites(): List<DiveSiteRecord> =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_DIVE_SITE }
            .map { AtJson.decodeFromString(DiveSiteRecord.serializer(), it.recordJson!!) }

    private fun ownDive(site: String? = null) {
        author = staleAlice
        sport = "dive"
        track = fixes
        recordExtra = site?.let { """"dive":{"site":"$it"},""" }.orEmpty()
    }

    private fun zones(zone: PrivacyZone? = null, hideEnds: Int = 0) {
        privacyZones =
            AtJson.encodeToString(
                PrivacyZonesRecord.serializer(),
                PrivacyZonesRecord("3m3ygsw4ngk2c", zones = listOfNotNull(zone), hideEndsMeters = hideEnds),
            )
    }

    @Test
    fun anOwnDiveAtANewPlacePublishesASiteAtItsEntry() = runTest {
        ownDive()
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            assertNull(ready.unavailable)
            assertEquals(DiveSiteRecord.DEFAULT_RADIUS, ready.radius)
            assertTrue(ready.namesDive, "a public dive with a public map")
            assertFalse(ready.canPublish, "no name yet")

            ready.name.edit { replace(0, length, "  Lighthouse  ") }
            awaitUntil { it.canPublish }.eventSink(CreateDiveSiteEvent.Publish)
            navigator.awaitPop()

            val record = r.queuedSites().single()
            assertEquals("Lighthouse", record.name)
            assertEquals(285_720_000 to 345_380_000, record.lat to record.lng, "the entry, not the exit")
            assertEquals(DiveSiteRecord.DEFAULT_RADIUS, record.radius)
            assertEquals("athlete" to "CC0-1.0", record.source to record.license)
            assertEquals(activityUri, record.derivedFrom?.uri?.value)
            assertEquals(setOf(AtUri(activityUri)), r.diveSites.createdFrom.value)
        }
    }

    @Test
    fun theNameStartsAsTheLogbooksSite() = runTest {
        ownDive(site = "Ras Mohammed")
        create { _ -> assertEquals("Ras Mohammed", awaitUntil { it.named }.name.text.toString()) }
    }

    @Test
    fun theRadiusStaysWithinItsBounds() = runTest {
        ownDive()
        create { _ ->
            val ready = awaitReady()
            ready.eventSink(CreateDiveSiteEvent.RadiusChanged(250))
            val moved = awaitUntil { it.radius == 250 }
            assertTrue(moved.outline != ready.outline, "the map shows the new radius")
            moved.eventSink(CreateDiveSiteEvent.RadiusChanged(5))
            awaitUntil { it.radius == DiveSiteRecord.MIN_RADIUS }
            moved.eventSink(CreateDiveSiteEvent.RadiusChanged(5_000))
            awaitUntil { it.radius == DiveSiteRecord.MAX_RADIUS }
        }
    }

    @Test
    fun anOnlyMeDiveNamesNoSourceDive() = runTest {
        ownDive()
        visibility = "self"
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            assertFalse(ready.namesDive)
            ready.name.edit { replace(0, length, "Canyon") }
            awaitUntil { it.canPublish }.eventSink(CreateDiveSiteEvent.Publish)
            navigator.awaitPop()
            assertNull(r.queuedSites().single().derivedFrom)
        }
    }

    @Test
    fun aDiveAlreadyAtASiteIsntOffered() = runTest {
        ownDive()
        viewExtra = """"diveSite":{"uri":"at://did:plc:carol/${Nsid.FEED_DIVE_SITE}/3kshark","name":"Shark Reef","status":"active"},"""
        create { _ -> assertEquals(CreateDiveSiteUnavailable.AtSite, awaitUnavailable()) }
    }

    @Test
    fun anEntryInsideAPrivacyZoneIsRefused() = runTest {
        ownDive()
        zones(PrivacyZone(latitudeE7 = 285_721_000, longitudeE7 = 345_380_000, radiusMeters = 200))
        create { r ->
            assertEquals(CreateDiveSiteUnavailable.InsideZone, awaitUnavailable())
            assertEquals(emptyList(), r.queuedSites())
        }
    }

    @Test
    fun anEntryInTheHiddenStartIsRefused() = runTest {
        ownDive()
        zones(hideEnds = 200)
        create { _ -> assertEquals(CreateDiveSiteUnavailable.HiddenStart, awaitUnavailable()) }
    }

    @Test
    fun aDiveWithNoPositionIsRefused() = runTest {
        ownDive()
        track = null
        create { _ -> assertEquals(CreateDiveSiteUnavailable.NoPosition, awaitUnavailable()) }
    }

    @Test
    fun aTrackThatCantBeReadSaysSoAndRetries() = runTest {
        ownDive()
        trackUnreachable = true
        create { _ ->
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(UiError.Kind.LoadDivePosition, failed.loadErrorKind)
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.unavailable, "not taken for no position")
            trackUnreachable = false
            failed.eventSink(CreateDiveSiteEvent.Retry)
            assertTrue(awaitReady().content.hasLoaded)
        }
    }

    @Test
    fun unreadableZonesOfferNothingUntilARetryReadsThem() = runTest {
        ownDive()
        failZones = true
        create { _ ->
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(UiError.Kind.LoadPrivacyZones, failed.loadErrorKind)
            assertNull(failed.outline)
            failZones = false
            failed.eventSink(CreateDiveSiteEvent.Retry)
            awaitReady()
        }
    }

    @Test
    fun aDuplicateIsShownAndNotPublished() = runTest {
        ownDive()
        checkDiveSite = """{"duplicates":[$existing],"remaining":9}"""
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Shark Reef again") }
            awaitUntil { it.canPublish }.eventSink(CreateDiveSiteEvent.Publish)
            val shown = awaitUntil { it.duplicates.isNotEmpty() }
            assertEquals("Shark Reef", shown.duplicates.single().currentName)
            assertEquals(emptyList(), r.queuedSites())
            shown.eventSink(CreateDiveSiteEvent.OpenSite(shown.duplicates.single().uri))
            assertEquals(DiveSiteScreen("at://did:plc:carol/${Nsid.FEED_DIVE_SITE}/3kshark"), navigator.awaitNextScreen())
        }
    }

    @Test
    fun noAllowanceLeftPublishesNothing() = runTest {
        ownDive()
        checkDiveSite = """{"duplicates":[],"remaining":0}"""
        create { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "One more") }
            awaitUntil { it.canPublish }.eventSink(CreateDiveSiteEvent.Publish)
            assertFalse(awaitUntil { it.limitReached }.canPublish)
            assertEquals(emptyList(), r.queuedSites())
        }
    }

    @Test
    fun aFailedCheckPublishesNothingAndCanBeRetried() = runTest {
        ownDive()
        checkDiveSite = null
        create { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Offline") }
            awaitUntil { it.canPublish }.eventSink(CreateDiveSiteEvent.Publish)
            val failed = awaitUntil { it.actionError != null }
            assertEquals(UiError.Kind.CheckDiveSite, failed.actionError?.kind)
            assertEquals(emptyList(), r.queuedSites())
            assertTrue(failed.canPublish)
            assertTrue(failed.content.hasLoaded, "not a failed load")
        }
    }

    @Test
    fun aDoubleTapPublishesOnce() = runTest {
        ownDive()
        val gate = CompletableDeferred<Unit>()
        checkDiveSiteGate = gate
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Once") }
            val named = awaitUntil { it.canPublish }
            named.eventSink(CreateDiveSiteEvent.Publish)
            named.eventSink(CreateDiveSiteEvent.Publish)
            awaitUntil { it.publishing }
            gate.complete(Unit)
            navigator.awaitPop()
            assertEquals(1, checkDiveSiteRequests)
            assertEquals(1, r.queuedSites().size)
        }
    }
}
