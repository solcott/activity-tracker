package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.INDEX_WAIT_MILLIS
import io.github.solcott.activitytracker.app.detail.MAX_INDEX_WAITS
import io.github.solcott.activitytracker.app.detail.awaitIndexed
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class AwaitIndexedTest {
    private val notFound = Outcome.Error(DataError.Api(listOf("Activity not found"), code = "NotFound"), Origin.Network)
    private val loaded = Outcome.Data("ride", Origin.Network)

    /** A read answering [answers] in turn, one per collection, each after Loading, as `outcomeFlow` does; [reads] counts them. */
    private class FakeRead(answers: List<Outcome<String>>) {
        var reads = 0

        val flow: Flow<Outcome<String>> = flow {
            emit(Outcome.Loading)
            emit(answers[minOf(reads++, answers.lastIndex)])
        }
    }

    @Test
    fun aJustWrittenActivityStaysLoadingUntilItsIndexed() = runTest {
        val reads = FakeRead(listOf(notFound, notFound, loaded))

        val emitted = reads.flow.awaitIndexed { true }.toList()

        assertEquals(listOf(Outcome.Loading, loaded), emitted)
        assertEquals(3, reads.reads)
        assertEquals(2 * INDEX_WAIT_MILLIS, currentTime)
    }

    @Test
    fun theErrorStandsAfterTheLastTry() = runTest {
        val reads = FakeRead(listOf(notFound))

        val emitted = reads.flow.awaitIndexed { true }.toList()

        assertEquals(listOf(Outcome.Loading, notFound), emitted)
        assertEquals(MAX_INDEX_WAITS + 1, reads.reads)
    }

    @Test
    fun anOfflineFailurePassesAtOnce() = runTest {
        val offline = Outcome.Error(DataError.Network, Origin.Network)
        val reads = FakeRead(listOf(offline, loaded))

        val emitted = reads.flow.awaitIndexed { true }.toList()

        assertEquals(listOf(Outcome.Loading, offline), emitted)
        assertEquals(1, reads.reads)
        assertEquals(0, currentTime)
    }

    @Test
    fun anActivityNotWrittenHereFailsAtOnce() = runTest {
        val reads = FakeRead(listOf(notFound, loaded))

        val emitted = reads.flow.awaitIndexed { false }.toList()

        assertEquals(listOf(Outcome.Loading, notFound), emitted)
        assertEquals(1, reads.reads)
    }
}
