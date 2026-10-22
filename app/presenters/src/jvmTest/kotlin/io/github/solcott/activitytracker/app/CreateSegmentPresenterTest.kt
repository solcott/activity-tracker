package io.github.solcott.activitytracker.app

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
        presenterTestOf({ CreateSegmentPresenter(createScreen, navigator, r.feed, r.activities, r.segments, r.publisher) }) {
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
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.CheckSegment, failed.error?.kind)
            assertEquals(emptyList(), r.queuedSegments())
            assertTrue(failed.canPublish, "it can be tried again")
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

        recordExtra = ""
        failZones = true
        create { _ -> assertEquals(CreateSegmentUnavailable.ZonesUnreadable, awaitUntil { it.unavailable != null }.unavailable) }
    }
}
