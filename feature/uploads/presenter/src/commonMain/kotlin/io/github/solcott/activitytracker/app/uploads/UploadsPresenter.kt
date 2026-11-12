package io.github.solcott.activitytracker.app.uploads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadSummary
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The upload queue read live from the device, so a write sent, a cancel or a retry shows at once. Each action runs once per item at a time
 * ([rememberSingleFlight]); cancelling asks first. Finished items a week old are dropped as it opens.
 */
@CircuitInject(UploadsScreen::class, SessionScope::class)
@Composable
fun UploadsPresenter(navigator: Navigator, uploads: UploadQueue, locationTracker: LocationTracker): UploadsState {
    var retryKey by remember { mutableIntStateOf(0) }
    val content = produceRetainedContentState<UploadQueueView?>(null, retryKey) { uploads.queueOutcomes }
    val summary by uploads.summary.collectAsState(UploadSummary())
    val timeLeft by uploads.estimate.collectAsState(null)
    var expanded by remember { mutableStateOf(persistentListOf<String>()) }
    var confirmingCancel by remember { mutableStateOf<String?>(null) }
    var confirmingCancelRest by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    LaunchedEffect(Unit) { runSuspendCatching { uploads.prune() } }
    val view = content.data
    val rkeys =
        listOfNotNull(view?.current?.uri?.rkey) +
            view?.own.orEmpty().mapNotNull { it.uri?.rkey } +
            view?.imports.orEmpty().flatMap { group -> group.live.mapNotNull { it.uri?.rkey } } +
            view?.refusedOther.orEmpty().map { it.rkey } +
            view?.imports.orEmpty().map { it.id }
    val busy = rkeys.filter { flights.isRunning(it) }.toImmutableList()

    fun act(key: Any, kind: UiError.Kind, block: suspend () -> Unit) {
        error = null
        flights.launch(key) { runSuspendCatching { block() }.onFailure { error = UiError.of(kind, it) } }
    }

    return UploadsState(
        content = content,
        importsPaused = summary.importsPaused,
        serverPausedUntil = summary.serverPausedUntil,
        timeLeft = timeLeft,
        isOpenOnly = !locationTracker.isSupported,
        expandedImports = expanded,
        busy = busy,
        changingPause = flights.isRunning(UploadsEvent.PauseImports) || flights.isRunning(UploadsEvent.ResumeImports),
        clearing = flights.isRunning(UploadsEvent.ClearFinished),
        confirmingCancel = confirmingCancel,
        confirmingCancelRest = confirmingCancelRest,
        error = error,
    ) { event ->
        when (event) {
            UploadsEvent.Back -> navigator.pop()
            UploadsEvent.Retry -> retryKey++
            is UploadsEvent.Cancel -> confirmingCancel = event.rkey
            is UploadsEvent.CancelRest -> confirmingCancelRest = event.batchId
            UploadsEvent.Dismiss -> {
                confirmingCancel = null
                confirmingCancelRest = null
            }
            is UploadsEvent.ConfirmCancel -> {
                confirmingCancel = null
                act(event.rkey, UiError.Kind.CancelUpload) {
                    // It started meanwhile: kept, and the athlete is told why.
                    check(uploads.cancel(event.rkey)) { "Already uploading" }
                }
            }
            is UploadsEvent.ConfirmCancelRest -> {
                confirmingCancelRest = null
                act(event.batchId, UiError.Kind.CancelUpload) { uploads.cancelRest(event.batchId) }
            }
            is UploadsEvent.SendAgain -> act(event.rkey, UiError.Kind.RetryChange) { uploads.retry(event.rkey) }
            UploadsEvent.PauseImports -> act(UploadsEvent.PauseImports, UiError.Kind.PauseImports) { uploads.pauseImports() }
            UploadsEvent.ResumeImports -> act(UploadsEvent.ResumeImports, UiError.Kind.PauseImports) { uploads.resumeImports() }
            UploadsEvent.ClearFinished -> act(UploadsEvent.ClearFinished, UiError.Kind.ClearUploads) { uploads.clearFinished() }
            is UploadsEvent.ShowAll -> if (event.batchId !in expanded) expanded = expanded.adding(event.batchId)
            is UploadsEvent.OpenActivity -> navigator.goTo(ActivityDetailScreen(event.uri))
        }
    }
}
