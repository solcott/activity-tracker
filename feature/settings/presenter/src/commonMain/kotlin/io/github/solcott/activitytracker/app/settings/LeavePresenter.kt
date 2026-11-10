package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.LeaveJob
import io.github.solcott.activitytracker.data.auth.LeaveProgress
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.auth.SharedPlaces
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.export.DataExporter
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.textfield.rememberTextFieldState

/** The one flight for starting the leave, so a double tap starts it once. */
private object StartLeave

/** The one flight for the export's actions on this screen. */
private object LeaveExportAction

/**
 * Leave Activity Tracker (auth/leaving). Before the athlete confirms: the summary, the shared places that stay, what this device loses, the
 * export, and the grant; confirming checks the PDS answers (reading the athlete's segments and sites again) before [LeaveJob.begin] records
 * the leave, so nothing is discarded when it can't run. Once a leave runs, its progress, and Retry after a refusal.
 */
@Suppress("LongParameterList", "LongMethod") // Circuit's screen and navigator, then what Metro injects; one state, built in one place.
@CircuitInject(LeaveScreen::class, SessionScope::class)
@Composable
fun LeavePresenter(
    screen: LeaveScreen,
    navigator: Navigator,
    clients: SessionClients,
    auth: AuthRepository,
    job: LeaveJob,
    check: SignOutCheck,
    exports: DataExports,
    exporter: DataExporter,
    recorder: ActivityRecorder,
): LeaveState {
    val did = clients.did
    val confirmHandle = rememberTextFieldState()
    val flights = rememberSingleFlight()
    var error by remember { mutableStateOf<UiError?>(null) }
    val progress by job.progress.collectAsState()
    val recording by recorder.state.collectAsState()
    val exportProgress by remember(did) { exports.progress(did) }.collectAsState(null)
    var places by remember { mutableStateOf<SharedPlacesSummary>(SharedPlacesSummary.Loading) }
    LaunchedEffect(Unit) {
        // After a restart, or signing in again mid-leave, the app shell opens this screen to carry the leave on: only its progress shows.
        if (screen.resume) {
            job.resume()
            return@LaunchedEffect
        }
        places =
            runSuspendCatching { job.sharedPlaces() }
                .fold({ it.summary() }, { SharedPlacesSummary.Failed(UiError.of(UiError.Kind.LoadSharedPlaces, it)) })
    }
    // Read once, when the screen opens: leaving discards whatever is there by then anyway.
    val pending by produceState<PendingWork?>(null) { if (!screen.resume) value = runSuspendCatching { check.pending() }.getOrNull() }

    // The PDS's answer about Spaces can land after the screen opens, and changes what the grant means.
    val support by clients.spacesSupport.collectAsState()
    val grant = remember(support) { clients.grantStatus }
    val export =
        when (exportProgress?.state) {
            DataExportEntity.STATE_RUNNING,
            DataExportEntity.STATE_PAUSED -> LeaveExport.Running
            DataExportEntity.STATE_READY -> LeaveExport.Ready
            else -> LeaveExport.None
        }
    val run = progress?.toRun() ?: if (screen.resume) LeaveRun() else null

    fun exportAction(block: suspend () -> Unit) {
        error = null
        flights.launch(LeaveExportAction) {
            runSuspendCatching { block() }.onFailure { error = UiError.of(UiError.Kind.StartDataExport, it) }
        }
    }

    val state =
        LeaveState(
            handle = clients.handle.value,
            confirmHandle = confirmHandle,
            places = places,
            pending = pending,
            recording = recording.inProgressFor(did.value),
            export = export,
            needsSignInAgain = grant is GrantStatus.Outdated && GrantFeature.Leaving in grant.missing,
            error = error,
            starting = flights.isRunning(StartLeave),
            run = run,
        ) {}
    return state.copy { event ->
        when (event) {
            LeaveEvent.Back -> if (run == null && !state.starting) navigator.pop()
            LeaveEvent.Export ->
                exportAction {
                    exporter.start()
                    exports.launch(did) { exporter.runCurrent() }
                }
            LeaveEvent.CancelExport -> exportAction { exports.cancel(did) }
            // The Account page, which opened this one, saves the export.
            LeaveEvent.SaveExport -> navigator.pop()
            LeaveEvent.SignInAgain -> flights.launch(StartLeave) { auth.signInAgain() }
            LeaveEvent.Leave ->
                if (state.canLeave) {
                    error = null
                    flights.launch(StartLeave) {
                        runSuspendCatching {
                            check(!job.exportRunning()) { "An export is running" }
                            // The PDS must answer before anything is discarded; the counts may have changed since, too.
                            places = job.sharedPlaces().summary()
                            job.begin()
                        }
                            .onFailure { error = UiError.of(UiError.Kind.StartLeave, it) }
                    }
                }
            LeaveEvent.Retry -> if (progress?.error != null) job.resume()
        }
    }
}

private fun SharedPlaces.summary() = SharedPlacesSummary.Loaded(activeSegments, otherSegments, activeSites, otherSites)

private fun LeaveProgress.toRun() = LeaveRun(step, recordsDeleted, pausedUntil, error?.let { UiError.of(UiError.Kind.Leave, it) })
