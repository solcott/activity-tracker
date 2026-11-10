package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.SessionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** What a session shows and when its background work runs (auth/leaving, dev/presenter-host). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRunnerTest {
    private val steps = FakeSteps()
    private val runner = SessionRunner(steps)

    private fun TestScope.start() {
        backgroundScope.launch { runner.run() }
        runCurrent()
    }

    @Test
    fun untilALeaveIsKnownOnlySpacesSupportAndUnitsRun() = runTest {
        start()

        assertEquals(SessionMode.Unknown, runner.mode.value)
        assertEquals(1, steps.spacesResolved)
        assertEquals(1, steps.unitsRefreshed)
        assertEquals(0, steps.checksStarted)
        assertEquals(0, steps.workStarted)
    }

    @Test
    fun withNoLeaveTheAppOpensAndWorkStartsAfterTheStillHereCheck() = runTest {
        steps.checkGate = CompletableDeferred()
        start()
        steps.leavingHere.value = false
        runCurrent()

        assertEquals(SessionMode.Open, runner.mode.value)
        assertEquals(1, steps.checksStarted)
        assertEquals(0, steps.workStarted, "nothing is sent for the account before the check answers")

        steps.checkGate?.complete(Unit)
        runCurrent()
        assertEquals(1, steps.workStarted)
    }

    @Test
    fun aLeaveRecordedHereShowsOnlyTheLeaveAndRunsNoWork() = runTest {
        start()
        steps.leavingHere.value = true
        runCurrent()

        assertEquals(SessionMode.Leaving, runner.mode.value)
        assertEquals(0, steps.checksStarted)
        assertEquals(0, steps.workStarted)
    }

    @Test
    fun leavingElsewhereStopsTheWorkUntilTheAthleteRejoins() = runTest {
        start()
        steps.leavingHere.value = false
        runCurrent()
        assertEquals(1, steps.workStarted)

        steps.left.value = true
        runCurrent()
        assertEquals(SessionMode.LeftElsewhere, runner.mode.value)
        assertEquals(1, steps.workStopped)

        steps.left.value = false
        runCurrent()
        assertEquals(SessionMode.Open, runner.mode.value)
        assertEquals(2, steps.workStarted)
        assertEquals(1, steps.checksStarted, "the check is asked once a session")
    }

    @Test
    fun aLeaveStartedHereStopsTheWork() = runTest {
        start()
        steps.leavingHere.value = false
        runCurrent()
        assertEquals(1, steps.workStarted)

        steps.leavingHere.value = true
        runCurrent()
        assertEquals(SessionMode.Leaving, runner.mode.value)
        assertEquals(1, steps.workStopped)
    }

    @Test
    fun aCheckCutShortByALeaveIsAskedAgain() = runTest {
        steps.checkGate = CompletableDeferred()
        start()
        steps.leavingHere.value = false
        runCurrent()
        assertEquals(1, steps.checksStarted)

        steps.leavingHere.value = true
        runCurrent()
        steps.leavingHere.value = false
        runCurrent()
        assertEquals(2, steps.checksStarted)
        assertEquals(0, steps.workStarted)

        steps.checkGate?.complete(Unit)
        runCurrent()
        assertEquals(1, steps.workStarted)
    }
}

private class FakeSteps : SessionSteps {
    /** Null until "Room answers". */
    val leavingHere = MutableStateFlow<Boolean?>(null)
    override val left = MutableStateFlow(false)
    var checkGate: CompletableDeferred<Unit>? = null
    var spacesResolved = 0
    var unitsRefreshed = 0
    var checksStarted = 0
    var workStarted = 0
    var workStopped = 0

    override val leaving: Flow<Boolean>
        get() = leavingHere.filterNotNull()

    override suspend fun resolveSpacesSupport() {
        spacesResolved++
    }

    override suspend fun refreshUnits() {
        unitsRefreshed++
    }

    override suspend fun checkStillHere() {
        checksStarted++
        checkGate?.await()
    }

    override suspend fun runWork() {
        workStarted++
        try {
            awaitCancellation()
        } finally {
            workStopped++
        }
    }
}
