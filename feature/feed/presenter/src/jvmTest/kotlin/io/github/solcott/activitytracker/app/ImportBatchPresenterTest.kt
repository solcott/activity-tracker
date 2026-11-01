package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.feed.ImportBatchEvent
import io.github.solcott.activitytracker.app.feed.ImportBatchPresenter
import io.github.solcott.activitytracker.app.feed.ImportBatchScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** One batch import's activities, opened from its timeline card (feed/import-cards). */
class ImportBatchPresenterTest {
    private val fixture = SessionTestFixture()
    private val bob = Did("did:plc:bob")
    private val screen = ImportBatchScreen(bob.value, "3m5abcdefghij", "Bob")

    private fun ride(rkey: String) =
        ActivityView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            record = ActivityRecord(Sport.RIDE, Instant.parse("2019-03-02T08:00:00Z"), 3600, Instant.parse("2026-10-30T12:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-10-30T12:00:01Z"),
            viewer = ActivityViewerState(),
        )

    @Test
    fun theBatchLoadsAndOpensAnActivity() = runTest {
        fixture.importBatch = listOf(ride("3kh1"), ride("3kh0"))
        val repos = fixture.repos()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ ImportBatchPresenter(screen, navigator, repos.feed, repos.activities, repos.profiles) }) {
            val loaded = awaitUntil { it.activities.items.size == 2 }
            assertEquals("Bob", loaded.name)
            assertEquals(listOf("3kh1", "3kh0"), loaded.activities.items.map { it.uri.rkey })
            loaded.eventSink(ImportBatchEvent.Activity(ActivityListEvent.Open(loaded.activities.items.first())))
            assertEquals(ActivityDetailScreen(ride("3kh1").uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysSoAndRetries() = runTest {
        fixture.importBatch = null
        val repos = fixture.repos()
        presenterTestOf({ ImportBatchPresenter(screen, FakeNavigator(screen), repos.feed, repos.activities, repos.profiles) }) {
            val failed = awaitUntil { it.activities.refresh is LoadStatus.Failed }
            fixture.importBatch = listOf(ride("3kh0"))
            failed.eventSink(ImportBatchEvent.Retry)
            assertEquals(
                1,
                awaitUntil { it.activities.refresh is LoadStatus.Idle && it.activities.items.isNotEmpty() }.activities.items.size,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLikeShowsOnTheListedActivity() = runTest {
        fixture.importBatch = listOf(ride("3kh0"))
        val repos = fixture.repos()
        presenterTestOf({ ImportBatchPresenter(screen, FakeNavigator(screen), repos.feed, repos.activities, repos.profiles) }) {
            val loaded = awaitUntil { it.activities.items.isNotEmpty() }
            loaded.eventSink(ImportBatchEvent.Activity(ActivityListEvent.ToggleLike(loaded.activities.items.single())))
            val liked = awaitUntil { it.activities.items.single().viewer?.like != null }
            assertNotNull(liked.activities.items.single().viewer?.like)
            assertEquals(1, liked.activities.items.single().likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
