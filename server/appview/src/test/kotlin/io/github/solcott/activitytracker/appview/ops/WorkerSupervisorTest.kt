package io.github.solcott.activitytracker.appview.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerSupervisorTest {
    private val registry = WorkerRegistry()

    private fun TestScope.supervisor(parent: CoroutineScope = backgroundScope) =
        WorkerSupervisor(parent, registry, testScheduler.timeSource)

    @Test
    fun aCrashedLoopIsRestartedAfterAPauseWhileOthersKeepRunning() = runTest {
        val supervisor = supervisor()
        var runs = 0
        var otherRounds = 0
        supervisor.loop("handle-sweep") {
            runs++
            if (runs == 1) error("boom")
            awaitCancellation()
        }
        supervisor.loop("label-sweep") { handle ->
            while (true) {
                otherRounds++
                handle.success()
                delay(100)
            }
        }
        runCurrent()
        registry["handle-sweep"]!!.let {
            assertEquals(WorkerState.RESTARTING, it.state)
            assertEquals(1, it.restarts)
            assertEquals("IllegalStateException: boom", it.lastError)
        }
        advanceTimeBy(1.seconds + 1.seconds / 10)
        assertEquals(2, runs)
        assertEquals(WorkerState.RUNNING, registry["handle-sweep"]!!.state)
        assertTrue(otherRounds >= 10, "the other worker kept running: $otherRounds rounds")
    }

    @Test
    fun repeatedCrashesWaitLongerEachTimeUpToFiveMinutes() = runTest {
        val supervisor = supervisor()
        val startedAt = mutableListOf<Long>()
        supervisor.loop("segment-matcher") {
            startedAt += currentTime
            error("crash")
        }
        advanceTimeBy(30.minutes)
        val pauses = startedAt.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), pauses.take(4))
        assertTrue(pauses.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(5.minutes.inWholeMilliseconds, pauses.last())
    }

    @Test
    fun theBackoffResetsAfterAHealthyRun() {
        assertEquals(1.seconds, WorkerSupervisor.pauseAfter(0))
        assertEquals(8.seconds, WorkerSupervisor.pauseAfter(3))
        assertEquals(5.minutes, WorkerSupervisor.pauseAfter(40))
    }

    @Test
    fun aRunAfterTenHealthyMinutesStartsTheBackoffOver() = runTest {
        val supervisor = supervisor()
        val startedAt = mutableListOf<Long>()
        supervisor.loop("jetstream") {
            startedAt += currentTime
            if (startedAt.size == 3) delay(11.minutes)
            error("dropped")
        }
        advanceTimeBy(20.minutes)
        // 1 s, 2 s, then a healthy run of 11 minutes: the next pause is back to 1 s.
        assertEquals(1_000L, startedAt[1] - startedAt[0])
        assertEquals(2_000L, startedAt[2] - startedAt[1])
        assertEquals(11.minutes.inWholeMilliseconds + 1_000L, startedAt[3] - startedAt[2])
    }

    @Test
    fun aLoopThatReturnsCountsAsACrash() = runTest {
        val supervisor = supervisor()
        supervisor.loop("moderation-access-log") {}
        runCurrent()
        assertEquals(WorkerState.RESTARTING, registry["moderation-access-log"]!!.state)
        assertEquals(1, registry["moderation-access-log"]!!.restarts)
    }

    @Test
    fun aOneOffThatCompletesIsFinishedAndNotRunAgain() = runTest {
        val supervisor = supervisor()
        var runs = 0
        supervisor.oneOff("segment-groups-backfill") { runs++ }
        advanceTimeBy(10.minutes)
        assertEquals(1, runs)
        assertEquals(WorkerState.FINISHED, registry["segment-groups-backfill"]!!.state)
    }

    @Test
    fun aOneOffThatFailsIsRunAgainUntilItCompletes() = runTest {
        val supervisor = supervisor()
        var runs = 0
        supervisor.oneOff("segment-groups-backfill") { if (++runs < 3) error("not yet") }
        advanceTimeBy(10.minutes)
        assertEquals(3, runs)
        assertEquals(WorkerState.FINISHED, registry["segment-groups-backfill"]!!.state)
        assertEquals(2, registry["segment-groups-backfill"]!!.restarts)
    }

    @Test
    fun shutdownStopsEveryWorkerWithoutARestart() = runTest {
        val app = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val supervisor = supervisor(app)
        var runs = 0
        supervisor.loop("handle-sweep") {
            runs++
            awaitCancellation()
        }
        supervisor.stream("jetstream") { awaitCancellation() }
        runCurrent()
        app.cancel()
        advanceTimeBy(10.minutes)
        assertEquals(1, runs)
        assertEquals(listOf(WorkerState.STOPPED, WorkerState.STOPPED), registry.snapshot().map { it.state })
        assertEquals(0, registry["handle-sweep"]!!.restarts)
    }
}
