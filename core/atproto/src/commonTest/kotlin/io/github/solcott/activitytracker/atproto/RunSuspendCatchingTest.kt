package io.github.solcott.activitytracker.atproto

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RunSuspendCatchingTest {
    @Test fun successReturnsValue() = runTest { assertEquals(42, runSuspendCatching { 42 }.getOrThrow()) }

    @Test
    fun exceptionBecomesFailure() = runTest {
        val result = runSuspendCatching { error("boom") }
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun cancellationExceptionIsRethrown() = runTest {
        assertFailsWith<CancellationException> { runSuspendCatching { throw CancellationException("cancelled") } }
    }

    @Test
    fun cancellingJobDoesNotProduceFailure() = runTest {
        val started = CompletableDeferred<Unit>()
        var result: Result<Unit>? = null
        val job = launch {
            result = runSuspendCatching {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertNull(result)
    }
}
