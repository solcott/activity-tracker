package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.ExperimentalForInheritanceCircuitTestApi
import kotlin.time.Duration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * circuit-test's `presenterTestOf`, with the presenter composed on the test thread only. Turbine collects on an unconfined dispatcher, so
 * circuit-test's composition resumes on whichever thread resumed it (Room's and Ktor's IO threads) while the test thread edits state and
 * sends events, and Molecule's immediate clock isn't thread-safe: under load that lost or overflowed emissions. Here the composition runs
 * on a [StandardTestDispatcher] sharing `runTest`'s scheduler, as a presenter runs on the main thread in the app.
 */
suspend fun <UiState : CircuitUiState> presenterTestOf(
    presentFunction: @Composable () -> UiState,
    timeout: Duration? = null,
    name: String? = null,
    policy: SnapshotMutationPolicy<UiState> = structuralEqualityPolicy(),
    block: suspend CircuitReceiveTurbine<UiState>.() -> Unit,
) {
    val scheduler = checkNotNull(currentCoroutineContext()[TestCoroutineScheduler]) { "presenterTestOf runs inside runTest" }
    moleculeFlow(RecompositionMode.Immediate, body = presentFunction).flowOn(StandardTestDispatcher(scheduler)).test(timeout, name) {
        DistinctTurbine(this, policy).block()
    }
}

/** circuit-test's `Presenter.test`, through [presenterTestOf]. */
suspend fun <UiState : CircuitUiState> Presenter<UiState>.test(
    timeout: Duration? = null,
    name: String? = null,
    policy: SnapshotMutationPolicy<UiState> = structuralEqualityPolicy(),
    block: suspend CircuitReceiveTurbine<UiState>.() -> Unit,
) = presenterTestOf({ present() }, timeout, name, policy, block)

/** circuit-test's own [CircuitReceiveTurbine], which it keeps internal: [awaitItem] skips states equivalent to the last one. */
@OptIn(ExperimentalForInheritanceCircuitTestApi::class)
private class DistinctTurbine<UiState : CircuitUiState>(
    private val delegate: ReceiveTurbine<UiState>,
    private val policy: SnapshotMutationPolicy<UiState>,
) : CircuitReceiveTurbine<UiState>, ReceiveTurbine<UiState> by delegate {
    private var lastItem: UiState? = null

    override suspend fun awaitUnchanged() {
        val next = delegate.awaitItem()
        if (next != lastItem) throw AssertionError("Expected unchanged item but received $next. Previous was $lastItem.")
    }

    override suspend fun awaitItem(): UiState {
        while (true) {
            val last = lastItem
            val next = delegate.awaitItem()
            lastItem = next
            if (last == null || !policy.equivalent(last, next)) return next
        }
    }
}
