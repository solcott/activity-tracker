package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.solcott.activitytracker.data.activity.NotSentYetException
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.location.LocationSample
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The owner's export: [state] is null on someone else's activity or when nothing can be exported. */
internal class Export(val state: ActivityExportState?, val export: (ExportFormat) -> Unit, val handedOff: () -> Unit)

/** What the owner's loaded [track] and streams allow, in [ExportFormat] order. */
internal fun exportFormats(track: List<LocationSample>?, hasStreams: Boolean): ImmutableList<ExportFormat> =
    ActivityExporter.formats(track, hasStreams).sorted().toImmutableList()

/**
 * Exporting the signed-in athlete's own [activity] (null for someone else's) as one of [formats], which come from what the screen loaded
 * ([exportFormats]); the export itself reads the track and streams fresh. One runs at a time, and its file waits in the state until the UI
 * hands it off.
 */
@Composable
internal fun rememberExport(
    activity: ActivityView?,
    formats: ImmutableList<ExportFormat>,
    hasLaps: Boolean,
    exporter: ActivityExporter,
    flights: SingleFlight,
): Export {
    var file by remember(activity?.uri) { mutableStateOf<ExportedFile?>(null) }
    var error by remember(activity?.uri) { mutableStateOf<UiError?>(null) }
    if (activity == null || formats.isEmpty()) return Export(null, {}, {})
    val key = ActivityDetailEvent.Export::class
    return Export(
        ActivityExportState(formats, busy = flights.isRunning(key), error = error, file = file, hasLaps = hasLaps),
        export = { format ->
            flights.launch(key) {
                error = null
                runSuspendCatching { exporter.export(activity, format) }
                    .onSuccess { file = it }
                    // No detail: the cause is a read that failed, which the localized message says better than its exception.
                    .onFailure {
                        error = UiError(if (it is NotSentYetException) UiError.Kind.ExportNotSent else UiError.Kind.ExportActivity, null)
                    }
            }
        },
        handedOff = { file = null },
    )
}
