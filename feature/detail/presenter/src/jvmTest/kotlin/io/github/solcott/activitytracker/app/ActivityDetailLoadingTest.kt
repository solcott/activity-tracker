package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** How the activity page loads (ui/content-loading): first load, retry, reloads under the page, coming back, and waiting for the index. */
class ActivityDetailLoadingTest : ActivityDetailTestBase() {
    private val heartRate =
        """{"uri":"$activityUri","visibility":"public","channels":["heartRate"],"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null]}}"""
    private val hill = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val efforts =
        """[{"segment":{"uri":"$hill","name":"Hill","sport":"run","distanceMeters":1000,"status":"active"},"activity":"$activityUri",""" +
            """"startOffset":120,"elapsed":611,"startedOn":"2026-09-30"}]"""

    private fun ActivityDetailState.settled() = page() != null && !content.isLoading

    @Test
    fun showsLoadingThenThePage() = runTest {
        detail(repos()) {
            val first = awaitItem()
            assertFalse(first.content.hasLoaded)
            assertTrue(first.content.isLoading)
            assertEquals(activityUri, awaitLoaded().activity.uri.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFirstLoadOfflineSaysWhyAndRetryLoadsIt() = runTest {
        unreachable = true
        detail(repos()) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.page())

            unreachable = false
            val gate = CompletableDeferred<Unit>().also { activityGate = it }
            failed.eventSink(ActivityDetailEvent.Retry)
            val retrying = awaitUntil { it.content.isLoading }
            assertNull(retrying.page(), "still the first load: no page yet")
            assertNull(retrying.content.errorOrNull)
            gate.complete(Unit)
            awaitLoaded()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aReloadKeepsThePageWhileItRuns() = runTest {
        detail(repos()) {
            val loaded = awaitUntil { it.settled() }
            val gate = CompletableDeferred<Unit>().also { activityGate = it }
            loaded.eventSink(ActivityDetailEvent.Retry)
            val reloading = awaitUntil { it.content.isLoading }
            assertNotNull(reloading.page(), "the page stays under the refresh")
            gate.complete(Unit)
            awaitUntil { it.settled() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedReloadKeepsThePageItsChartsAndEfforts() = runTest {
        streams = heartRate
        segmentsFromAppView = efforts
        detail(repos()) {
            val loaded = awaitUntil { it.settled() && it.page()?.charts != null && it.page()?.segments?.isNotEmpty() == true }
            unreachable = true
            loaded.eventSink(ActivityDetailEvent.Retry)
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            val page = assertNotNull(failed.page())
            assertNotNull(page.charts)
            assertEquals(listOf("Hill"), page.segments.flatMap { it.rows }.map { it.label })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsThePageAtOnce() = runTest {
        streams = heartRate
        segmentsFromAppView = efforts
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        val presenter = presenterOf(r)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the screen it opened while it's on the back stack.
                if (shown) presenter() else ActivityDetailState(eventSink = {})
            }
        }) {
            awaitUntil { it.settled() && it.page()?.charts != null && it.page()?.segments?.isNotEmpty() == true }
            registry.saveAll()
            shown = false
            awaitUntil { it.page() == null }
            unreachable = true
            shown = true
            val back = assertNotNull(awaitItem().page(), "no loading state in between")
            assertNotNull(back.charts)
            assertEquals(listOf("Hill"), back.segments.flatMap { it.rows }.map { it.label })
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertNotNull(failed.page()?.charts, "kept through the failed reload")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCountMovedByTheViewerIsntMovedAgainByTheReload() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        detail(repos()) {
            val loaded = awaitUntil { it.settled() && it.page()?.comments?.roots?.size == 2 }.page()!!
            val count = loaded.activity.commentCount
            loaded.eventSink(ActivityDetailEvent.BlockCommenter(loaded.comments.roots.first().uri))
            val moved = awaitUntil { it.page()?.activity?.commentCount == count - 1 }
            // The AppView still lists the comment: the reload's answer counts the block itself.
            val gate = CompletableDeferred<Unit>().also { activityGate = it }
            moved.eventSink(ActivityDetailEvent.Retry)
            awaitUntil { it.content.isLoading }
            gate.complete(Unit)
            val reloaded = awaitUntil { it.settled() }.page()!!
            assertEquals(count - 1, reloaded.activity.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aJustSavedActivityStaysLoadingUntilIndexed() = runTest {
        notIndexedFor = 2
        presenterTestOf(presenterOf(repos(), ActivityDetailScreen(activityUri, suggestOnOpen = true))) {
            var state = awaitItem()
            while (state.page() == null) {
                assertNull(state.content.errorOrNull, "no error while it waits")
                state = awaitItem()
            }
            assertEquals(3, activityRequests)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onceIndexedLateItsChartsAndCommentsLoadToo() = runTest {
        notIndexedFor = 2
        streams = heartRate
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        presenterTestOf(presenterOf(repos(), ActivityDetailScreen(activityUri, suggestOnOpen = true))) {
            val page = awaitUntil { it.page()?.let { p -> p.charts != null && p.comments.roots.size == 1 } == true }.page()!!
            assertNull(
                page.comments.refresh as? io.github.solcott.uistate.LoadStatus.Failed,
                "read again once the AppView had the activity",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesMissingActivityFailsAtOnce() = runTest {
        notIndexedFor = 5
        detail(repos()) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals("NotFound", (failed.content.errorOrNull as DataError.Api).code)
            assertEquals(1, activityRequests)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedChartsAndEffortsShowThePageWithoutThem() = runTest {
        streams = """{"broken""""
        segmentsFromAppView = null
        detail(repos()) {
            val page = awaitUntil { it.settled() }.page()!!
            assertNull(page.charts)
            assertTrue(page.segments.isEmpty())
            assertNull(page.likeError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aChangeOfUnitsRebuildsTheLapTable() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":[],"data":{"time":[0,300,600],"heartRate":[140,150,160],"laps":[""" +
                """{"start":0,"elapsed":300,"moving":295,"distance":1609,"avgSpeed":5364}]}}"""
        val r = repos()
        detail(r) {
            awaitUntil { it.page()?.charts?.laps?.isNotEmpty() == true }
            // As when the athlete's preference, set on another device, is read.
            profileUnits = UnitSystem.Imperial.token
            r.units.refresh()
            val miles = awaitUntil { it.page()?.charts?.laps?.firstOrNull()?.distance?.let { d -> d < 1.01 } == true }
            assertEquals(1.0, miles.page()!!.charts!!.laps.first().distance!!, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whoLikedItOfflineSaysWhy() = runTest {
        detail(repos()) {
            val loaded = awaitUntil { it.settled() }.page()!!
            unreachable = true
            loaded.eventSink(ActivityDetailEvent.ToggleLikesList)
            val failed = awaitLikes { it.error != null }.likesList!!
            assertEquals(UiError(UiError.Kind.LoadLikes, DataError.Network), failed.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theOwnersTrackAndOriginalShowAtOnceWhenComingBack() = runTest {
        author = staleAlice
        val start = Instant.parse("2026-09-30T10:00:00Z")
        track = (0..100).map { LocationSample(51.5, -0.12 + it * 0.0001, null, speed = null, accuracy = 5.0, time = start + it.seconds) }
        original = "Morning_Ride.fit" to byteArrayOf(1, 2, 3)
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        val presenter = presenterOf(r)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) presenter() else ActivityDetailState(eventSink = {})
            }
        }) {
            awaitUntil { it.page()?.route?.ends != null && it.page()?.export?.original != null }
            registry.saveAll()
            shown = false
            awaitUntil { it.page() == null }
            shown = true
            val back = assertNotNull(awaitItem().page(), "no loading state in between")
            assertNotNull(back.route?.ends, "the full route, not the trimmed polyline first")
            assertEquals("Morning_Ride.fit", back.export?.original?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nothingOfTheOwnersLoadsOnSomeoneElsesActivity() = runTest {
        val start = Instant.parse("2026-09-30T10:00:00Z")
        track = (0..100).map { LocationSample(51.5, -0.12 + it * 0.0001, null, speed = null, accuracy = 5.0, time = start + it.seconds) }
        original = "Bobs_Ride.fit" to byteArrayOf(1, 2, 3)
        detail(repos()) {
            val page = awaitUntil { it.settled() }.page()!!
            assertNull(page.export)
            assertFalse(page.canHideRouteEnds)
            assertEquals(0, originalReads)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
