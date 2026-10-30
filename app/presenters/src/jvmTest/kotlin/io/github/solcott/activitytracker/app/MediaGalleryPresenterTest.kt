package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.media.MediaGalleryEvent
import io.github.solcott.activitytracker.app.media.MediaGalleryPresenter
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryState
import io.github.solcott.activitytracker.app.report.ReportOuterEvent
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** The full-screen photos (ui/content-loading): a first-load phase, retry, retention, and a failed block kept apart from the load. */
class MediaGalleryPresenterTest : ActivityTestBase() {
    private val gallery = MediaGalleryScreen(activityUri, 0)

    private val photo =
        "\"media\":[{\"\$type\":\"app.activitytracker.feed.defs#imageView\",\"fullsize\":\"http://appview.test/img/media/did:plc:bob/bafkphoto\"}],"

    @Test
    fun loadsThenShowsThePhotos() = runTest {
        viewExtra = photo
        val r = repos()
        presenterTestOf({ MediaGalleryPresenter(gallery, FakeNavigator(gallery), r.feed, r.profiles, r.blocks, r.reports) }) {
            val first = awaitItem()
            assertFalse(first.content.hasLoaded)
            assertEquals(1, awaitUntil { it.items.isNotEmpty() }.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryLoadsIt() = runTest {
        viewExtra = photo
        offline = true
        val r = repos()
        presenterTestOf({ MediaGalleryPresenter(gallery, FakeNavigator(gallery), r.feed, r.profiles, r.blocks, r.reports) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertFalse(failed.content.hasLoaded)
            assertNull(failed.error, "a load failure isn't an action's")

            offline = false
            failed.eventSink(MediaGalleryEvent.Retry)
            assertEquals(1, awaitUntil { it.items.isNotEmpty() }.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsThePhotosAtOnceAndKeepsThemThroughAFailedReload() = runTest {
        viewExtra = photo
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the screen it opened while it's on the back stack.
                if (shown) MediaGalleryPresenter(gallery, FakeNavigator(gallery), r.feed, r.profiles, r.blocks, r.reports)
                else MediaGalleryState()
            }
        }) {
            awaitUntil { it.items.isNotEmpty() && !it.content.isLoading }
            registry.saveAll()
            shown = false
            awaitUntil { it.items.isEmpty() }
            offline = true
            shown = true
            assertEquals(1, awaitItem().items.size, "no loading state in between")
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(1, failed.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedBlockIsTheActionsErrorAndKeepsThePhotos() = runTest {
        viewExtra = photo
        // A session that can't write blocks to its interactions space.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ MediaGalleryPresenter(gallery, FakeNavigator(gallery), r.feed, r.profiles, r.blocks, r.reports) }) {
            val loaded = awaitUntil { it.items.isNotEmpty() && it.canReport }
            loaded.eventSink(MediaGalleryEvent.ReportPhoto)
            val open = awaitUntil { it.reporting.sheet != null }
            open.reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Block(Did("did:plc:bob"))))
            awaitUntil { it.reporting.confirmBlock != null }.reporting.eventSink(ReportingEvent.ConfirmBlock(true))
            val failed = awaitUntil { it.error != null }
            assertNotNull(failed.error)
            assertNull(failed.content.errorOrNull)
            assertEquals(1, failed.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
