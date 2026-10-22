package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.solcott.activitytracker.data.activity.NotSentYetException
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull

/** The owner's export: [state] is null on someone else's activity or when nothing can be exported. */
internal class Export(
    val state: ActivityExportState?,
    val export: (ExportFormat) -> Unit,
    val downloadOriginal: () -> Unit,
    val handedOff: () -> Unit,
)

/** What the owner's loaded [track] and streams allow, UDDF only on a dive ([isDive]), in [ExportFormat] order. */
internal fun exportFormats(track: List<LocationSample>?, hasStreams: Boolean, isDive: Boolean): ImmutableList<ExportFormat> =
    ActivityExporter.formats(track, hasStreams, isDive).sorted().toImmutableList()

/**
 * Exporting the signed-in athlete's own [activity] (null for someone else's) as one of [formats], which come from what the screen loaded
 * ([exportFormats]), or downloading the file it was imported from; the export itself reads the track and streams, or the original, fresh.
 * One runs at a time, and its file waits in the state until the UI hands it off.
 */
@Composable
internal fun rememberExport(
    activity: ActivityView?,
    loadKey: Int,
    formats: ImmutableList<ExportFormat>,
    hasLaps: Boolean,
    exporter: ActivityExporter,
    flights: SingleFlight,
): Export {
    var file by remember(activity?.uri) { mutableStateOf<ExportedFile?>(null) }
    var error by remember(activity?.uri) { mutableStateOf<UiError?>(null) }
    val latest by rememberUpdatedState(activity)
    // Retained with the page; without it (or when it can't be read, quietly) there's nothing to download.
    val original = remember {
        snapshotFlow { latest }.filterNotNull().distinctUntilChangedBy { it.uri }
    }
        .produceRetainedContentState<ActivityView, OriginalFile?>(null, loadKey) { own ->
            outcomeFlow { exporter.originalInfo(own)?.let { OriginalFile(it.fileName, it.format) } }
        }
        .data
    if (activity == null || (formats.isEmpty() && original == null)) return Export(null, {}, {}, {})
    // The export and the download share one flight: one file at a time.
    val key = ActivityDetailEvent.Export::class
    fun run(notSent: UiError.Kind, failed: UiError.Kind, build: suspend () -> ExportedFile) =
        flights.launch(key) {
            error = null
            runSuspendCatching { build() }
                .onSuccess { file = it }
                // No detail: the cause is a read that failed, which the localized message says better than its exception.
                .onFailure { error = UiError(if (it is NotSentYetException) notSent else failed) }
        }
    return Export(
        ActivityExportState(formats, busy = flights.isRunning(key), error = error, file = file, hasLaps = hasLaps, original = original),
        export = { format -> run(UiError.Kind.ExportNotSent, UiError.Kind.ExportActivity) { exporter.export(activity, format) } },
        downloadOriginal = { run(UiError.Kind.OriginalNotSent, UiError.Kind.DownloadOriginal) { exporter.original(activity) } },
        handedOff = { file = null },
    )
}
