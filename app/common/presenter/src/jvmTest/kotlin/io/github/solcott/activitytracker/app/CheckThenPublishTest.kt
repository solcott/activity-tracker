package io.github.solcott.activitytracker.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class CheckThenPublishTest {
    private var publishes = 0
    private var published = 0

    private fun TestScope.publishAfter(
        flights: SingleFlight,
        checks: PublishChecks<String>,
        check: suspend () -> PublishCheck<String>,
        publish: suspend () -> Unit = { publishes++ },
    ) =
        flights.checkThenPublish(
            key = "publish",
            checks = checks,
            check = check,
            checkError = UiError.Kind.CheckSegment,
            publish = publish,
            publishError = UiError.Kind.PublishSegment,
            onPublished = { published++ },
        )

    @Test
    fun aDoubleTapPublishesOnce() = runTest {
        val flights = SingleFlight(this)
        val checks = PublishChecks<String>()
        val gate = CompletableDeferred<Unit>()
        val check: suspend () -> PublishCheck<String> = {
            gate.await()
            PublishCheck(emptyList(), remaining = 3)
        }

        assertNotNull(publishAfter(flights, checks, check))
        assertNull(publishAfter(flights, checks, check))
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, publishes)
        assertEquals(1, published)
        assertFalse(flights.isRunning("publish"))
    }

    @Test
    fun duplicatesOrTheLimitStopThePublish() = runTest {
        val flights = SingleFlight(this)
        val checks = PublishChecks<String>()

        publishAfter(flights, checks, { PublishCheck(listOf("Pier"), remaining = 3) })
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Pier"), checks.duplicates)
        assertFalse(checks.limitReached)

        publishAfter(flights, checks, { PublishCheck(emptyList(), remaining = 0) })
        testScheduler.advanceUntilIdle()
        assertTrue(checks.duplicates.isEmpty())
        assertTrue(checks.limitReached)

        assertEquals(0, publishes)
        assertEquals(0, published)
        assertNull(checks.error)
    }

    @Test
    fun eachStepsFailureIsItsOwnKindAndTheNextTryClearsIt() = runTest {
        val flights = SingleFlight(this)
        val checks = PublishChecks<String>()

        publishAfter(flights, checks, { error("unreachable") })
        testScheduler.advanceUntilIdle()
        assertEquals(UiError.Kind.CheckSegment, checks.error?.kind)

        val gate = CompletableDeferred<Unit>()
        publishAfter(flights, checks, { PublishCheck(emptyList(), remaining = 1) }) {
            gate.await()
            error("refused")
        }
        testScheduler.runCurrent()
        assertNull(checks.error)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(UiError.Kind.PublishSegment, checks.error?.kind)
        assertEquals(0, published)
    }

    @Test
    fun clearingDuplicatesKeepsTheLimit() {
        val checks = PublishChecks<String>()
        checks.checked(PublishCheck(listOf("Pier"), remaining = 0))
        checks.clearDuplicates()
        assertTrue(checks.duplicates.isEmpty())
        assertTrue(checks.limitReached)
    }
}
