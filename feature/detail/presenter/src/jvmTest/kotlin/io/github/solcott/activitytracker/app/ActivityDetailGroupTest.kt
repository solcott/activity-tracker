package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The activity page's partners and the owner's Leave group / Rejoin group (feed/group-activities). */
class ActivityDetailGroupTest : ActivityDetailTestBase() {
    private val carolsRide = "at://did:plc:carol/${Nsid.FEED_ACTIVITY}/3kcarol1"
    private val partners = """"group":[{"author":$carol,"uri":"$carolsRide"}],"""
    private val storedTrack =
        $$"""{"activity":"$$activityUri","createdAt":"2026-09-30T10:31:00Z",""" +
            $$""""data":{"$type":"blob","ref":{"$link":"bafydata"},"mimeType":"application/json","size":10}}"""

    private suspend fun presenter(
        navigator: FakeNavigator = FakeNavigator(screen),
        repos: Repos? = null,
    ): @Composable () -> ActivityDetailState {
        val r = repos ?: repos()
        return {
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
                r.diveSites,
                r.groups,
            )
        }
    }

    @Test
    fun partnersAreListedAndOpenTheirActivity() = runTest {
        viewExtra = partners
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenter(navigator)) {
            val page = awaitUntil { it.loaded()?.partners?.isNotEmpty() == true }.page()!!
            assertEquals(listOf(carolsRide), page.partners.map { it.uri.value })
            assertNull(page.ownGroup, "not offered on someone else's activity")
            page.eventSink(ActivityDetailEvent.OpenActivity(AtUri(carolsRide)))
            assertEquals(ActivityDetailScreen(carolsRide), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theOwnerLeavesAndRejoinsWritingOnce() = runTest {
        author = staleAlice
        viewExtra = partners
        groupPrefs = """{"mode":"anyone","updatedAt":"2026-10-01T00:00:00Z"}"""
        segmentTrack = storedTrack
        val r = repos()
        presenterTestOf(presenter(repos = r)) {
            val page = awaitUntil { it.loaded()?.ownGroup != null }.page()!!
            assertEquals(false, page.ownGroup?.left)
            assertEquals(1, page.partners.size)

            page.eventSink(ActivityDetailEvent.LeaveGroup)
            page.eventSink(ActivityDetailEvent.LeaveGroup)
            val left = awaitUntil { it.loaded()?.ownGroup?.left == true && it.loaded()?.ownGroup?.busy == false }.page()!!
            assertTrue(left.partners.isEmpty(), "no one listed once the owner leaves")
            val queued = r.db.outbox().pending(alice.value).filter { it.collection == Nsid.FEED_SEGMENT_TRACK }
            assertEquals(1, queued.size, "the double tap queued one rewrite")
            val record = AtJson.parseToJsonElement(queued.single().recordJson!!).jsonObject
            assertEquals("true", record["groupExcluded"]?.jsonPrimitive?.content)
            assertEquals(
                "bafydata",
                record["data"]?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content,
                "same points",
            )

            left.eventSink(ActivityDetailEvent.RejoinGroup)
            awaitUntil { it.loaded()?.ownGroup?.left == false && it.loaded()?.ownGroup?.busy == false }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun itIsntOfferedWhileGroupingIsOffOrWithoutAStoredTrack() = runTest {
        author = staleAlice
        groupPrefs = """{"mode":"off","updatedAt":"2026-10-01T00:00:00Z"}"""
        segmentTrack = storedTrack
        presenterTestOf(presenter()) {
            assertNull(awaitUntil { it.loaded() != null }.page()!!.ownGroup)
            cancelAndIgnoreRemainingEvents()
        }

        groupPrefs = """{"mode":"connected","updatedAt":"2026-10-01T00:00:00Z"}"""
        segmentTrack = null
        presenterTestOf(presenter()) {
            assertNull(awaitUntil { it.loaded() != null }.page()!!.ownGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
