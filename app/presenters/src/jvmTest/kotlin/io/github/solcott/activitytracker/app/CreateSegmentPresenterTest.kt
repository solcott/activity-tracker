package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.CreateSegmentEvent
import io.github.solcott.activitytracker.app.segment.CreateSegmentPresenter
import io.github.solcott.activitytracker.app.segment.CreateSegmentScreen
import io.github.solcott.activitytracker.app.segment.CreateSegmentState
import io.github.solcott.activitytracker.app.segment.CreateSegmentUnavailable
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.SelectionCheck
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.location.LocationSample
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/** Creating a segment from one's own activity (segments/segment-creation). */
class CreateSegmentPresenterTest : ActivityDetailTestBase() {
    private val start = Instant.parse("2026-09-30T10:00:00Z")

    /** About 2.75 km east at 51.5° N, a sample every ~7 m. */
    private val route =
        (0..400).map { LocationSample(51.5, -0.12 + it * 0.0001, null, speed = null, accuracy = 5.0, time = start + it.seconds) }

    private val createScreen = CreateSegmentScreen(activityUri)

    private val existing =
        """{"uri":"at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill","cid":"bafyseg","creator":$carol,""" +
            """"record":{"name":"Hill","sport":"run","line":"_p~iF~ps|U_ulLnnqC","source":"athlete","license":"CC0-1.0",""" +
            """"createdAt":"2026-09-01T00:00:00Z"},"distanceMeters":800,"status":"active"}"""

    private suspend fun create(
        navigator: FakeNavigator = FakeNavigator(createScreen),
        block: suspend app.cash.turbine.ReceiveTurbine<CreateSegmentState>.(Repos) -> Unit,
    ) {
        val r = repos()
        presenterTestOf({ CreateSegmentPresenter(createScreen, navigator, r.feed, r.activities, r.privacy, r.segments, r.publisher) }) {
            block(r)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CreateSegmentState>.awaitReady() = awaitUntil {
        it.route != null && it.selection !is SelectionCheck.CrossesGap
    }

    private suspend fun Repos.queuedSegments(): List<SegmentRecord> =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_SEGMENT }
            .map { AtJson.decodeFromString(SegmentRecord.serializer(), it.recordJson!!) }

    private fun ownRun() {
        author = staleAlice
        track = route
    }

    @Test
    fun publishesTheWholeFirstStretchNamingAPublicActivity() = runTest {
        ownRun()
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            assertEquals("run", ready.sport)
            assertTrue(ready.namesActivity)
            assertIs<SelectionCheck.Ok>(ready.selection)
            assertTrue(ready.highlight != null)
            assertFalse(ready.canPublish, "no name yet")

            ready.name.edit { replace(0, length, "  Riverside  ") }
            val named = awaitUntil { it.canPublish }
            named.eventSink(CreateSegmentEvent.Publish)
            navigator.awaitPop()

            val record = r.queuedSegments().single()
            assertEquals("Riverside", record.name)
            assertEquals("run", record.sport)
            assertEquals(activityUri, record.derivedFrom?.uri?.value)
            assertEquals(setOf(AtUri(activityUri)), r.segments.createdFrom.value)
            assertEquals(1, checkRequests)
        }
    }

    @Test
    fun aFollowersActivityIsNotNamed() = runTest {
        ownRun()
        visibility = "followers"
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            assertFalse(ready.namesActivity)
            ready.name.edit { replace(0, length, "Lane") }
            awaitUntil { it.canPublish }.eventSink(CreateSegmentEvent.Publish)
            navigator.awaitPop()
            assertNull(r.queuedSegments().single().derivedFrom)
        }
    }

    @Test
    fun aShortRideIsRefused() = runTest {
        ownRun()
        sport = "ride"
        create { _ ->
            val ready = awaitReady()
            ready.eventSink(CreateSegmentEvent.SelectionChanged(0f, 350f))
            val short = awaitUntil { it.selection is SelectionCheck.TooShort }
            assertEquals(SelectionCheck.TooShort(500), short.selection)
            assertNull(short.highlight)
            short.name.edit { replace(0, length, "Sprint") }
            assertFalse(awaitUntil { it.named }.canPublish)
        }
    }

    @Test
    fun aStretchAcrossAZoneIsRefused() = runTest {
        ownRun()
        // 150 m around the route's middle.
        privacyZones =
            AtJson.encodeToString(
                PrivacyZonesRecord.serializer(),
                PrivacyZonesRecord(
                    "3m3ygsw4ngk2c",
                    zones = listOf(PrivacyZone(latitudeE7 = 515_000_000, longitudeE7 = -1_000_000, radiusMeters = 150)),
                ),
            )
        create { _ ->
            val ready = awaitReady()
            val gap = ready.end
            assertTrue(gap < ready.lengthMeters, "the first stretch ends at the zone")
            ready.eventSink(CreateSegmentEvent.SelectionChanged(gap - 300f, gap + 300f))
            assertEquals(SelectionCheck.CrossesGap, awaitUntil { it.end > gap }.selection)
        }
    }

    @Test
    fun duplicatesAreShownInsteadOfPublishing() = runTest {
        ownRun()
        checkSegment = """{"duplicates":[$existing],"remaining":9}"""
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Hill again") }
            awaitUntil { it.canPublish }.eventSink(CreateSegmentEvent.Publish)
            val shown = awaitUntil { it.duplicates.isNotEmpty() }
            assertEquals("Hill", shown.duplicates.single().record.name)
            assertEquals(emptyList(), r.queuedSegments())

            shown.eventSink(CreateSegmentEvent.OpenSegment(shown.duplicates.single().uri))
            assertEquals(SegmentScreen("at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"), navigator.awaitNextScreen())

            // A new stretch clears them, since they were found for the old one.
            shown.eventSink(CreateSegmentEvent.SelectionChanged(0f, 600f))
            assertTrue(awaitUntil { it.end == 600f }.duplicates.isEmpty())
        }
    }

    @Test
    fun theDailyLimitStopsPublishing() = runTest {
        ownRun()
        checkSegment = """{"duplicates":[],"remaining":0}"""
        create { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "One more") }
            awaitUntil { it.canPublish }.eventSink(CreateSegmentEvent.Publish)
            val limited = awaitUntil { it.limitReached }
            assertFalse(limited.canPublish)
            assertEquals(emptyList(), r.queuedSegments())
        }
    }

    @Test
    fun aFailedCheckPublishesNothing() = runTest {
        ownRun()
        checkSegment = null
        create { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Offline") }
            awaitUntil { it.canPublish }.eventSink(CreateSegmentEvent.Publish)
            val failed = awaitUntil { it.actionError != null }
            assertEquals(UiError.Kind.CheckSegment, failed.actionError?.kind)
            assertEquals(emptyList(), r.queuedSegments())
            assertTrue(failed.canPublish, "it can be tried again")
            assertTrue(failed.content.hasLoaded, "not shown as a failed load")
            assertEquals("Offline", failed.name.text.toString())
            assertEquals(ready.end, failed.end, "the stretch stays")
        }
    }

    @Test
    fun aDoubleTapPublishesOnce() = runTest {
        ownRun()
        val gate = CompletableDeferred<Unit>()
        checkGate = gate
        val navigator = FakeNavigator(createScreen)
        create(navigator) { r ->
            val ready = awaitReady()
            ready.name.edit { replace(0, length, "Once") }
            val named = awaitUntil { it.canPublish }
            named.eventSink(CreateSegmentEvent.Publish)
            named.eventSink(CreateSegmentEvent.Publish)
            awaitUntil { it.publishing }
            gate.complete(Unit)
            navigator.awaitPop()
            assertEquals(1, checkRequests)
            assertEquals(1, r.queuedSegments().size)
        }
    }

    @Test
    fun whySegmentsCantBeMade() = runTest {
        author = staleAlice
        create { _ -> assertEquals(CreateSegmentUnavailable.NoRoute, awaitUntil { it.unavailable != null }.unavailable) }

        track = route
        recordExtra = """"subSport":"treadmill","""
        create { _ -> assertEquals(CreateSegmentUnavailable.NoSportGroup, awaitUntil { it.unavailable != null }.unavailable) }
    }

    @Test
    fun itShowsLoadingUntilAllThreeHaveLoaded() = runTest {
        ownRun()
        val gate = CompletableDeferred<Unit>()
        activityGate = gate
        create { _ ->
            val loading = awaitItem()
            assertTrue(loading.content.isLoading)
            assertNull(loading.unavailable, "not that there's no route")
            gate.complete(Unit)
            assertTrue(awaitReady().content.hasLoaded)
        }
    }

    @Test
    fun aRouteThatCantBeReadIsAFailedLoadNotNoRoute() = runTest {
        ownRun()
        trackUnreachable = true
        create { _ ->
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(UiError.Kind.LoadRoute, failed.loadErrorKind)
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.unavailable)

            trackUnreachable = false
            failed.eventSink(CreateSegmentEvent.Retry)
            val ready = awaitReady()
            assertNull(ready.content.errorOrNull)
        }
    }

    @Test
    fun unreadableZonesAreAFailedLoadUntilARetryReadsThem() = runTest {
        ownRun()
        failZones = true
        create { _ ->
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(UiError.Kind.LoadPrivacyZones, failed.loadErrorKind)
            assertNull(failed.route, "nothing to cut a segment from")
            assertNull(failed.unavailable)

            failZones = false
            failed.eventSink(CreateSegmentEvent.Retry)
            assertTrue(awaitReady().content.hasLoaded)
        }
    }

    @Test
    fun aLaterLoadLeavesTheStretchAndName() = runTest {
        ownRun()
        create { _ ->
            val ready = awaitReady()
            ready.eventSink(CreateSegmentEvent.SelectionChanged(100f, 900f))
            ready.name.edit { replace(0, length, "Kept") }
            val chosen = awaitUntil { it.end == 900f && it.named }
            // Zones saved elsewhere meanwhile; a reload doesn't re-cut the route under the chosen stretch.
            privacyZones =
                AtJson.encodeToString(
                    PrivacyZonesRecord.serializer(),
                    PrivacyZonesRecord("3m3ygsw4ngk2d", zones = listOf(PrivacyZone(515_000_000, -1_000_000, 150))),
                )
            val reads = activityRequests
            chosen.eventSink(CreateSegmentEvent.Retry)
            while (activityRequests == reads) yield()
            // A move after the reload answers shows the route as first cut.
            chosen.eventSink(CreateSegmentEvent.SelectionChanged(100f, 950f))
            val moved = awaitUntil { it.end == 950f }
            assertEquals(chosen.route, moved.route, "not re-cut by the new zone")
            assertEquals(100f, moved.start)
            assertEquals("Kept", moved.name.text.toString())
        }
    }

    @Test
    fun comingBackFromADuplicateShowsTheRouteAndStretchAtOnce() = runTest {
        ownRun()
        checkSegment = """{"duplicates":[$existing],"remaining":9}"""
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the segment it opened while it's on the back stack.
                if (shown)
                    CreateSegmentPresenter(
                        createScreen,
                        FakeNavigator(createScreen),
                        r.feed,
                        r.activities,
                        r.privacy,
                        r.segments,
                        r.publisher,
                    )
                else CreateSegmentState()
            }
        }) {
            val ready = awaitReady()
            ready.eventSink(CreateSegmentEvent.SelectionChanged(100f, 900f))
            ready.name.edit { replace(0, length, "Hill again") }
            awaitUntil { it.end == 900f && it.canPublish }.eventSink(CreateSegmentEvent.Publish)
            val shownDuplicate = awaitUntil { it.duplicates.isNotEmpty() }
            registry.saveAll()
            shown = false
            awaitUntil { it.route == null }
            trackUnreachable = true
            shown = true
            val back = awaitItem()
            assertEquals(shownDuplicate.route, back.route, "no loading state in between")
            assertEquals(100f, back.start)
            assertEquals(900f, back.end)
            assertEquals("Hill", back.duplicates.single().record.name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
