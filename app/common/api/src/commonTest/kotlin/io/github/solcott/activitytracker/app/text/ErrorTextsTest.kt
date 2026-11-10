package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals

/** ui/error-messages: an error says what failed, and why only in the athlete's terms, never with the failure's own text. */
class ErrorTextsTest {
    @Test
    fun aKindsStringIsNamedByRule() {
        assertEquals("error_load_feed", ErrorTexts.kindName(UiError.Kind.LoadFeed))
        assertEquals("error_load_import_batch", ErrorTexts.kindName(UiError.Kind.LoadImportBatch))
        assertEquals("error_save_profile", ErrorTexts.kindName(UiError.Kind.SaveProfile))
    }

    @Test fun theIrregularNameIsListed() = assertEquals("error_discard_left", ErrorTexts.kindName(UiError.Kind.DiscardLeftElsewhere))

    @Test
    fun aReasonWorthSayingFollowsWhatFailed() =
        assertEquals(
            AppText.named("error_with_reason", AppText.named("error_load_feed"), AppText.named("error_reason_unreachable")),
            ErrorTexts.of(UiError(UiError.Kind.LoadFeed, DataError.Network)),
        )

    @Test
    fun serverAndUnreadableFailuresSayWhy() {
        val what = AppText.written("Sign-in failed")
        assertEquals(
            AppText.named("error_with_reason", what, AppText.named("error_reason_server")),
            ErrorTexts.failure(what, DataError.Http(503)),
        )
        assertEquals(
            AppText.named("error_with_reason", what, AppText.named("error_reason_unreadable")),
            ErrorTexts.failure(what, DataError.Serialization),
        )
    }

    @Test
    fun othersSayOnlyWhatFailed() {
        val what = AppText.named("error_load_feed")
        assertEquals(what, ErrorTexts.of(UiError(UiError.Kind.LoadFeed)))
        assertEquals(what, ErrorTexts.of(UiError(UiError.Kind.LoadFeed, DataError.Http(404))))
        assertEquals(what, ErrorTexts.failure(what, DataError.Unknown(IllegalStateException("boom"), "boom")))
    }

    @Test
    fun anUnclassifiedFailureSaysOnlyWhatFailed() =
        assertEquals(
            AppText.named("error_save_profile"),
            ErrorTexts.of(UiError.of(UiError.Kind.SaveProfile, IllegalStateException("boom"))),
        )
}
