package io.github.solcott.activitytracker.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class SingleFlightTest {
    @Test
    fun aRepeatIsDroppedWhileTheFirstRuns() = runTest {
        val flights = SingleFlight(this)
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        assertNotNull(
            flights.launch("save") {
                runs++
                gate.await()
            }
        )
        assertTrue(flights.isRunning("save"))
        assertNull(flights.launch("save") { runs++ })

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertFalse(flights.isRunning("save"))
        assertEquals(1, runs)

        // Once it's done, the next tap runs again.
        flights.launch("save") { runs++ }
        testScheduler.advanceUntilIdle()
        assertEquals(2, runs)
    }

    @Test
    fun keysAreIndependent() = runTest {
        val flights = SingleFlight(this)
        val gate = CompletableDeferred<Unit>()
        flights.launch("a") { gate.await() }
        assertNotNull(flights.launch("b") { gate.await() })
        assertTrue(flights.isRunning("a") && flights.isRunning("b"))
        gate.complete(Unit)
    }

    @Test
    fun launchLatestReplacesTheRunningWork() = runTest {
        val flights = SingleFlight(this)
        val first = flights.launchLatest("read") { CompletableDeferred<Unit>().await() }
        var secondRan = false
        flights.launchLatest("read") { secondRan = true }
        testScheduler.advanceUntilIdle()
        assertTrue(first.isCancelled)
        assertTrue(secondRan)
        assertFalse(flights.isRunning("read"))
    }

    @Test
    fun cancellingFromOutsideFreesTheKey() = runTest {
        val flights = SingleFlight(this)
        // Cancelled before it was ever dispatched, as when a new search query drops the page being loaded.
        assertNotNull(flights.launch("page") { CompletableDeferred<Unit>().await() }).cancel()
        // The key is released once the cancelled job completes, on its dispatcher's next turn.
        testScheduler.runCurrent()
        assertFalse(flights.isRunning("page"))
        assertNotNull(flights.launch("page") {})
    }
}
