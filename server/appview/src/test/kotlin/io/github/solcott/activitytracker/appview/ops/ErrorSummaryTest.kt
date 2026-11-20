package io.github.solcott.activitytracker.appview.ops

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorSummaryTest {
    @Test
    fun typeAndFirstLineOfTheMessage() {
        assertEquals("IOException: Connection refused", ErrorSummary.of(IOException("Connection refused\n\tat somewhere")))
    }

    @Test
    fun typeAloneWithoutAMessage() {
        assertEquals("IllegalStateException", ErrorSummary.of(IllegalStateException()))
    }

    @Test
    fun aLongMultiLineRemoteMessageKeepsOnlyItsFirstLineWithinTheLimit() {
        val message = "x".repeat(10_000) + "\nsecond line"
        val summary = ErrorSummary.of(XrpcException(502, "UpstreamFailure", message))
        assertTrue(summary.length <= ErrorSummary.MAX_LENGTH)
        assertTrue(summary.startsWith("XrpcException: UpstreamFailure: xxx"))
        assertFalse("second line" in summary)
    }

    @Test
    fun anXrpcErrorNamesItsError() {
        assertEquals("XrpcException: SpaceNotFound: no such space", ErrorSummary.of(XrpcException(400, "SpaceNotFound", "no such space")))
        assertEquals("XrpcException: UpstreamFailure", ErrorSummary.of(XrpcException(502, "UpstreamFailure", null)))
    }

    @Test
    fun aDecodingFailureIsRecordedByTypeAloneWithNothingOfTheRecord() {
        val sentinel = "alice-private-sentinel-7c1f"
        val failure = runCatching { AtJson.parseToJsonElement("""{"activity": "$sentinel", "pieces": [7""") }.exceptionOrNull()!!
        assertTrue(sentinel in failure.message.orEmpty(), "the decoder quotes its input, which is what this guards against")
        val summary = ErrorSummary.of(IllegalStateException("matching failed", failure))
        assertFalse(sentinel in summary)
        assertEquals("IllegalStateException", summary)
        assertFalse(sentinel in ErrorSummary.of(failure))
    }

    @Test
    fun ownTextIsOneBoundedLine() {
        assertEquals("not stream data", ErrorSummary.text("not stream data\nmore"))
        assertEquals(ErrorSummary.MAX_LENGTH, ErrorSummary.text("y".repeat(900)).length)
    }
}
