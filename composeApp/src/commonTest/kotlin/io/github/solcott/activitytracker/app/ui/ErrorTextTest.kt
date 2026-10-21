package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.error_reason_offline
import io.github.solcott.activitytracker.composeApp.resources.error_reason_server
import io.github.solcott.activitytracker.composeApp.resources.error_reason_unreadable
import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** ui/error-messages: an error says why only in the athlete's terms, never with the failure's own text. */
class ErrorTextTest {
    @Test fun offline() = assertEquals(Res.string.error_reason_offline, errorReason(DataError.Network))

    @Test
    fun serverFailures() {
        assertEquals(Res.string.error_reason_server, errorReason(DataError.Http(503)))
        assertEquals(Res.string.error_reason_server, errorReason(DataError.Api(listOf("boom"), code = "InternalServerError")))
    }

    @Test fun unreadableReply() = assertEquals(Res.string.error_reason_unreadable, errorReason(DataError.Serialization))

    @Test
    fun othersSayOnlyWhatFailed() {
        assertNull(errorReason(DataError.Http(404)))
        assertNull(errorReason(DataError.Api(listOf("Segment not found"), code = "NotFound")))
        assertNull(errorReason(DataError.Unknown(IllegalStateException("boom"), "boom")))
    }

    @Test
    fun anUnclassifiedFailureSaysOnlyWhatFailed() {
        val error = UiError.of(UiError.Kind.SaveProfile, IllegalStateException("boom"))
        assertNull(errorReason(assertIs<DataError.Unknown>(error.reason)))
    }
}
