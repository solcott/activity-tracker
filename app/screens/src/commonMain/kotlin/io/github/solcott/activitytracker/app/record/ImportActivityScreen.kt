package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlin.time.Duration
import kotlinx.io.bytestring.ByteString

/** Import an activity from a file a device recorded. FIT only for now; GPX and TCX are next. */
@CircuitSerializable(SessionScope::class) data object ImportActivityScreen : Screen

@Immutable
data class ImportActivityState(
    /** A picked file is being read. */
    val reading: Boolean,
    /** What the file holds, once read. */
    val preview: ImportPreview?,
    /** Starts as the file's sport; the athlete can correct it. */
    val sport: String,
    val title: String,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (ImportActivityEvent) -> Unit,
) : CircuitUiState

/** The read file's summary, in SI units like the records it becomes. */
@Immutable
data class ImportPreview(
    val fileName: String,
    val elapsed: Duration,
    val movingSeconds: Long?,
    val distanceMeters: Double?,
    val elevationGainMeters: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val device: String?,
    /** Null for indoor activities. */
    val polyline: String?,
)

sealed interface ImportActivityEvent {
    /** The athlete picked a file. [name] carries the extension the format is chosen by. */
    data class FilePicked(val name: String, val bytes: ByteString) : ImportActivityEvent

    data class SportChanged(val value: String) : ImportActivityEvent

    data class TitleChanged(val value: String) : ImportActivityEvent

    data object Save : ImportActivityEvent

    data object Back : ImportActivityEvent
}
