package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.LeaveJob
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.location.ActivityRecorder

/** The one flight for discarding or rejoining: either ends what this screen is for, so neither runs twice or both at once. */
private object Answer

/**
 * The athlete left Activity Tracker on another device (auth/leaving). With nothing pending on this device, the session ends at once with
 * the "left" note; otherwise the athlete chooses Discard and sign out, or Rejoin, which lets the app shell bring the app back and upload.
 */
@CircuitInject(LeftElsewhereScreen::class, SessionScope::class)
@Composable
fun LeftElsewherePresenter(clients: SessionClients, job: LeaveJob, check: SignOutCheck, recorder: ActivityRecorder): LeftElsewhereState {
    var pending by remember { mutableStateOf<PendingWork?>(null) }
    var recording by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    fun discard() {
        error = null
        flights.launch(Answer) {
            runSuspendCatching { job.discardLeftElsewhere() }.onFailure { error = UiError.of(UiError.Kind.DiscardLeftElsewhere, it) }
        }
    }

    LaunchedEffect(Unit) {
        // A queue that can't be read counts as holding work, so nothing is dropped without asking.
        val read = runSuspendCatching { check.pending() }
        pending = read.getOrNull() ?: if (read.isFailure) PendingWork(0, importSaving = false) else null
        recording = recorder.state.value.inProgressFor(clients.did.value)
        checking = false
        if (pending == null && !recording) discard()
    }

    val work = pending
    return LeftElsewhereState(
        handle = clients.handle.value,
        // Nothing pending: the session is ending, so the screen keeps saying it's busy rather than flashing the choice.
        checking = checking || work == null && !recording && error == null,
        changes = work?.changes ?: 0,
        importSaving = work?.importSaving == true,
        exportPending = work?.exportPending == true,
        recording = recording,
        busy = flights.isRunning(Answer),
        error = error,
    ) { event ->
        when (event) {
            LeftElsewhereEvent.Discard -> discard()
            LeftElsewhereEvent.Rejoin -> {
                error = null
                flights.launch(Answer) { runSuspendCatching { clients.rejoin() }.onFailure { error = UiError.of(UiError.Kind.Rejoin, it) } }
            }
        }
    }
}
