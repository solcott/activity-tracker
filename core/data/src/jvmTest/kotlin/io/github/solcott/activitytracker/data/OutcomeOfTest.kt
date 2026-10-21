package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

class OutcomeOfTest {
    private suspend fun failure(e: Throwable): DataError {
        val outcome = outcomeOf { throw e }
        assertIs<Outcome.Error>(outcome)
        assertEquals(Origin.Network, outcome.origin)
        return outcome.cause
    }

    @Test fun valueIsData() = runTest { assertEquals(Outcome.Data(1, Origin.Cache), outcomeOf(Origin.Cache) { 1 }) }

    @Test
    fun nonXrpcErrorBodyIsHttp() = runTest {
        assertEquals(DataError.Http(502), failure(XrpcException(502, "HttpError", "<html>Bad gateway</html>")))
    }

    @Test
    fun xrpcErrorIsApiWithItsName() = runTest {
        assertEquals(DataError.Api(listOf("No segment"), code = "NotFound"), failure(XrpcException(400, "NotFound", "No segment")))
    }

    @Test
    fun xrpcErrorWithoutMessageUsesItsName() = runTest {
        assertEquals(DataError.Api(listOf("NotFound"), code = "NotFound"), failure(XrpcException(400, "NotFound", null)))
    }

    @Test fun ioFailureIsNetwork() = runTest { assertEquals(DataError.Network, failure(IOException("Connection refused"))) }

    @Test
    fun timeoutIsNetwork() = runTest { assertEquals(DataError.Network, failure(HttpRequestTimeoutException("https://appview", 10_000))) }

    @Test fun undecodableIsSerialization() = runTest { assertEquals(DataError.Serialization, failure(SerializationException("bad"))) }

    @Test
    fun anythingElseIsUnknownWithItsCause() = runTest {
        val e = IllegalStateException("boom")
        assertEquals(DataError.Unknown(e, "boom"), failure(e))
    }

    @Test fun cancellationIsRethrown() = runTest { assertFailsWith<CancellationException> { outcomeOf { throw CancellationException() } } }

    @Test
    fun flowLoadsThenAnswersOnEachCollection() = runTest {
        var calls = 0
        val flow = outcomeFlow { ++calls }
        assertEquals(listOf(Outcome.Loading, Outcome.Data(1, Origin.Network)), flow.toList())
        assertEquals(listOf(Outcome.Loading, Outcome.Data(2, Origin.Network)), flow.toList())
    }
}
