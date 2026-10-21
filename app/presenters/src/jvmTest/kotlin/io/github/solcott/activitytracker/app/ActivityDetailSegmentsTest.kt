package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.segment.CreateSegmentScreen
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The activity page's segments section (segments/segment-efforts): its efforts, in their own load that never holds up the page. */
class ActivityDetailSegmentsTest : ActivityDetailTestBase() {
    private val hill = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val bridge = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3ksbridge"

    @Suppress("LongParameterList") // The effort's fields a test sets.
    private fun effort(segment: String, name: String, offset: Int, elapsed: Int, best: Boolean, status: String = SegmentStatus.ACTIVE) =
        """{"segment":{"uri":"$segment","name":"$name","sport":"run","distanceMeters":1000,"status":"$status"},""" +
            """"activity":"$activityUri",""" +
            """"startOffset":$offset,"elapsed":$elapsed,"startedOn":"2026-09-30"${if (best) ""","best":true""" else ""}}"""

    /** Runs [block] on the page once [ready] holds for it, with its sink and the efforts it shows as "name elapsed[ PR]". */
    private suspend fun withDetail(
        navigator: FakeNavigator = FakeNavigator(screen),
        ready: (ActivityDetailState.Loaded) -> Boolean = { true },
        block: suspend ((ActivityDetailEvent) -> Unit, List<String>) -> Unit,
    ) {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                navigator,
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            val state = awaitUntil { s -> s.loaded()?.let(ready) == true } as ActivityDetailState.Loaded
            block(state.eventSink, state.segments.map { "${it.segment.name} ${it.elapsed}${if (it.best == true) " PR" else ""}" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listsTheEffortsInOrderWithTheirBest() = runTest {
        segmentsFromAppView = "[${effort(hill, "Hill", 120, 611, best = true)},${effort(bridge, "Bridge", 900, 95, best = false)}]"
        withDetail(ready = { it.segments.isNotEmpty() }) { _, shown -> assertEquals(listOf("Hill 611 PR", "Bridge 95"), shown) }
    }

    @Test
    fun noEffortsShowNoSection() = runTest {
        segmentsFromAppView = "[]"
        withDetail { _, shown -> assertTrue(shown.isEmpty()) }
    }

    @Test
    fun aFailedLoadShowsThePageWithoutTheSection() = runTest {
        segmentsFromAppView = null
        withDetail { _, shown -> assertTrue(shown.isEmpty()) }
    }

    @Test
    fun tappingAnEffortOpensItsSegment() = runTest {
        segmentsFromAppView = "[${effort(hill, "Hill", 120, 611, best = true)}]"
        val navigator = FakeNavigator(screen)
        withDetail(navigator, ready = { it.segments.isNotEmpty() }) { send, _ ->
            send(ActivityDetailEvent.SegmentOpened(AtUri(hill)))
            assertEquals(SegmentScreen(hill), navigator.awaitNextScreen())
        }
    }

    @Test
    fun effortsCarryTheirSegmentsStatus() = runTest {
        segmentsFromAppView = "[${effort(hill, "Hill", 120, 611, best = true, status = SegmentStatus.PROPOSED)}]"
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.segments?.isNotEmpty() == true }.loaded()!!
            assertEquals(SegmentStatus.PROPOSED, loaded.segments.single().segment.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val route =
        (0..300).map {
            LocationSample(
                51.5,
                -0.12 + it * 0.0001,
                null,
                speed = null,
                accuracy = 5.0,
                time = Instant.parse("2026-09-30T10:00:00Z") + it.seconds,
            )
        }

    /** Whether the page offers Create segment once [settled] holds: by default, once the owner's track is drawn. */
    private suspend fun canCreate(settled: (ActivityDetailState.Loaded) -> Boolean = { it.route?.ends != null }): Boolean {
        var can = false
        detail(repos()) {
            can = awaitUntil { s -> s.loaded()?.let { it.canCreateSegment || settled(it) } == true }.loaded()!!.canCreateSegment
            cancelAndIgnoreRemainingEvents()
        }
        return can
    }

    @Test
    fun theOwnerCanCreateASegmentFromTheirOutdoorRoute() = runTest {
        author = staleAlice
        track = route
        assertTrue(canCreate())
    }

    @Test
    fun noSegmentFromSomeoneElsesIndoorOrRoutelessActivity() = runTest {
        track = route
        assertFalse(canCreate { true }, "someone else's")

        author = staleAlice
        recordExtra = """"subSport":"treadmill","""
        assertFalse(canCreate(), "indoor")

        recordExtra = ""
        track = null
        assertFalse(canCreate { true }, "no track")
    }

    @Test
    fun noSegmentWhileTheZonesCantBeRead() = runTest {
        author = staleAlice
        track = route
        failZones = true
        assertFalse(canCreate())
    }

    @Test
    fun createSegmentOpensItsScreenAndAPublishedOneIsConfirmed() = runTest {
        author = staleAlice
        track = route
        val navigator = FakeNavigator(screen)
        val r = repos()
        withDetail(navigator, ready = { it.canCreateSegment }) { send, _ ->
            send(ActivityDetailEvent.CreateSegment)
            assertEquals(CreateSegmentScreen(activityUri), navigator.awaitNextScreen())
        }
        r.segments.markCreated(AtUri(activityUri))
        detail(r) {
            assertTrue(awaitUntil { it.loaded()?.segmentCreated == true }.loaded()!!.segmentCreated)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptySet(), r.segments.createdFrom.value, "said once, then forgotten when the page goes")
    }
}
