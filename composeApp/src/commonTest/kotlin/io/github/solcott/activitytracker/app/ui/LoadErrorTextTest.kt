package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.error_reason_offline
import io.github.solcott.activitytracker.composeApp.resources.error_reason_server
import io.github.solcott.activitytracker.composeApp.resources.error_reason_unreadable
import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ui/content-loading: a load error says why only in the athlete's terms, never with the failure's own text. */
class LoadErrorTextTest {
    @Test fun offline() = assertEquals(Res.string.error_reason_offline, loadErrorReason(DataError.Network))

    @Test
    fun serverFailures() {
        assertEquals(Res.string.error_reason_server, loadErrorReason(DataError.Http(503)))
        assertEquals(Res.string.error_reason_server, loadErrorReason(DataError.Api(listOf("boom"), code = "InternalServerError")))
    }

    @Test fun unreadableReply() = assertEquals(Res.string.error_reason_unreadable, loadErrorReason(DataError.Serialization))

    @Test
    fun othersSayOnlyWhatFailed() {
        assertNull(loadErrorReason(DataError.Http(404)))
        assertNull(loadErrorReason(DataError.Api(listOf("Segment not found"), code = "NotFound")))
        assertNull(loadErrorReason(DataError.Unknown(IllegalStateException("boom"), "boom")))
    }
}
