package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
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
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.export.DataExportProgress
import io.github.solcott.activitytracker.data.export.DataExporter
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.export.ExportArchive
import kotlinx.datetime.TimeZone

/** The one flight for asking and ending the session, so a second tap can neither ask twice nor end it twice. */
private object EndSession

/** The one flight for starting, cancelling and finishing with the export, so a double tap does each once. */
private object ExportFlight

@Suppress("LongParameterList", "CyclomaticComplexMethod") // Circuit's navigator, then what Metro injects; one branch per event.
@CircuitInject(AccountScreen::class, SessionScope::class)
@Composable
fun AccountPresenter(
    navigator: Navigator,
    clients: SessionClients,
    auth: AuthRepository,
    check: SignOutCheck,
    exports: DataExports,
    exporter: DataExporter,
): AccountState {
    // The PDS's answer about Spaces can land after the page opens, and changes what the grant means.
    val support by clients.spacesSupport.collectAsState()
    val grant = remember(support) { clients.grantStatus }
    var confirming by remember { mutableStateOf<EndingSession?>(null) }
    var askingKeptShared by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    // The export lives app-wide and outlives this page: the page follows it (auth/data-export).
    val progress by remember(clients.did) { exports.progress(clients.did) }.collectAsState(null)
    val readyId = progress?.takeIf { it.state == DataExportEntity.STATE_READY }?.id
    val archive by
        produceState<ExportArchive?>(null, readyId) { value = readyId?.let { exports.archive(it, TimeZone.currentSystemDefault()) } }

    // Dropping the session tears down the SessionGraph and returns to LoginScreen, so nothing after the end runs here.
    suspend fun end(signInAgain: Boolean) {
        if (signInAgain) auth.signInAgain() else auth.signOut()
    }

    fun ask(signInAgain: Boolean) {
        flights.launch(EndSession) {
            // A check that can't read the device's queue ends the session as asked: the queued writes stay on the device either way.
            val pending = runSuspendCatching { check.pending() }.getOrNull()
            if (pending == null) end(signInAgain) else confirming = EndingSession(signInAgain, pending)
        }
    }

    fun exportAction(kind: UiError.Kind, block: suspend () -> Unit) {
        exportError = null
        flights.launch(ExportFlight) { runSuspendCatching { block() }.onFailure { exportError = UiError.of(kind, it) } }
    }

    return AccountState(
        handle = clients.handle.value,
        did = clients.did.value,
        pdsHost = hostOf(clients.pdsUrl),
        grant = grant,
        confirming = confirming,
        ending = flights.isRunning(EndSession),
        export = statusOf(progress, archive),
        askingKeptShared = askingKeptShared,
        exportError = exportError,
    ) { event ->
        when (event) {
            AccountEvent.Back -> navigator.pop()
            AccountEvent.SignOut -> if (confirming == null) ask(signInAgain = false)
            AccountEvent.SignInAgain -> if (confirming == null) ask(signInAgain = true)
            is AccountEvent.Confirm -> {
                confirming = null
                flights.launch(EndSession) { end(event.signInAgain) }
            }
            AccountEvent.Dismiss -> confirming = null
            // Starting runs where the platform keeps it alive; a paused or failed export goes on from where it stopped.
            AccountEvent.Export ->
                exportAction(UiError.Kind.StartDataExport) {
                    exporter.start()
                    exports.launch(clients.did) { exporter.runCurrent() }
                }
            AccountEvent.CancelExport -> exportAction(UiError.Kind.StartDataExport) { exports.cancel(clients.did) }
            AccountEvent.ExportSaved -> readyId?.let { id -> exportAction(UiError.Kind.SaveDataExport) { exports.saved(id) } }
            AccountEvent.ExportShared -> askingKeptShared = true
            AccountEvent.ExportSaveFailed -> exportError = UiError(UiError.Kind.SaveDataExport)
            is AccountEvent.KeptShared -> {
                askingKeptShared = false
                if (event.kept) readyId?.let { id -> exportAction(UiError.Kind.SaveDataExport) { exports.saved(id) } }
            }
            AccountEvent.Leave -> navigator.goTo(LeaveScreen())
        }
    }
}

/** What the page shows of [progress]; a ready export shows as running until its [archive] is at hand, a moment later. */
internal fun statusOf(progress: DataExportProgress?, archive: ExportArchive?): DataExportStatus =
    when (progress?.state) {
        null -> DataExportStatus.None
        DataExportEntity.STATE_PAUSED -> DataExportStatus.Paused(progress.neededBytes)
        DataExportEntity.STATE_FAILED -> DataExportStatus.Failed
        DataExportEntity.STATE_READY if archive != null ->
            DataExportStatus.Ready(archive, progress.problems, progress.unsentChanges, progress.spacesIncluded)
        else ->
            DataExportStatus.Running(
                records = progress.records,
                filesDone = progress.blobsDone,
                filesTotal = progress.blobsTotal,
                activitiesDone = progress.activitiesDone,
                activitiesTotal = progress.activitiesTotal,
            )
    }

/** The host of [url], `pds.example.com` for `https://pds.example.com:443/`: what the athlete knows their server by. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { url }
