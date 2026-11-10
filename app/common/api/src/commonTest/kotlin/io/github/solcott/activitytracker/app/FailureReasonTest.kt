package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which failures say why (ui/error-messages). */
class FailureReasonTest {
    @Test fun aNetworkFailureIsUnreachable() = assertEquals(FailureReason.Unreachable, FailureReason.of(DataError.Network))

    @Test
    fun aServerFailureIsTheServers() {
        assertEquals(FailureReason.Server, FailureReason.of(DataError.Http(500)))
        assertEquals(FailureReason.Server, FailureReason.of(DataError.Http(503)))
        assertEquals(FailureReason.Server, FailureReason.of(DataError.Api(listOf("boom"), code = "InternalServerError")))
    }

    @Test fun aReplyThatCantBeReadIsUnreadable() = assertEquals(FailureReason.Unreadable, FailureReason.of(DataError.Serialization))

    @Test
    fun anythingElseSaysOnlyWhatFailed() {
        assertNull(FailureReason.of(DataError.Http(404)))
        assertNull(FailureReason.of(DataError.Api(listOf("Segment not found"), code = "NotFound")))
        assertNull(FailureReason.of(DataError.Unknown(IllegalStateException("boom"), "boom")))
    }
}
