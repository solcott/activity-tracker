package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

/**
 * Moderation on the detail page: what labels hide, and revealing it for this view only (`moderation/labels`). Reporting a comment is the
 * Comments screen's (`CommentsModerationTest`), the gallery's side `MediaGalleryPresenterTest`'s.
 */
class ActivityDetailModerationTest : ActivityDetailTestBase() {
    private fun labels(vararg values: String) =
        values.joinToString(",", "\"labels\":[", "],") { """{"src":"did:plc:labeler","val":"$it"}""" }

    @Test
    fun aHiddenActivityOpensBehindAWarningUntilRevealed() = runTest {
        viewExtra = labels("!hide")
        val r = repos()
        detail(r) {
            val loaded = awaitLoaded()
            assertEquals(ModerationDecision.Hidden, ModerationDecision.of(loaded.activity.labels, loaded.activity.author.labels, false))
            val key = Revealed.hiddenKey(activityUri)
            assertFalse(key in loaded.revealed)
            loaded.revealed.eventSink(key)
            val revealed = awaitUntil { it.page()?.revealed?.contains(key) == true }.page()!!
            assertFalse(activityUri in revealed.revealed, "its photos stay blurred until revealed on their own")
            cancelAndIgnoreRemainingEvents()
        }
        // A new view of the screen starts covered again.
        detail(r) {
            assertFalse(Revealed.hiddenKey(activityUri) in awaitLoaded().revealed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
