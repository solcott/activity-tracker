package io.github.solcott.activitytracker.app.uploads

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The upload queue (data/upload-queue): what the athlete saved or edited that is uploading, waiting, refused or done, with imports grouped,
 * cancel for what hasn't started, and one switch pausing imports. Opened from the feed's upload rows and after an import is saved.
 */
@CircuitSerializable(SessionScope::class) data object UploadsScreen : Screen

@Immutable
data class UploadsState(
    val content: ContentState<UploadQueueView?> = ContentState(null),
    /** Whether the athlete paused imports. */
    val importsPaused: Boolean = false,
    /** Until when the server paused uploads, while anything is uploading. */
    val serverPausedUntil: Instant? = null,
    /** About how long the imports' waiting activities take; null when there's nothing to go by. */
    val timeLeft: Duration? = null,
    /** Uploads go on only while the app is open (desktop and web). */
    val isOpenOnly: Boolean = false,
    /** The imports whose every activity is shown, not only the first [IMPORT_PREVIEW]. */
    val expandedImports: ImmutableList<String> = persistentListOf(),
    /** The rkeys of items being cancelled or sent again, and the ids of imports whose rest is being cancelled. */
    val busy: ImmutableList<String> = persistentListOf(),
    /** Whether pausing or resuming imports is under way. */
    val changingPause: Boolean = false,
    /** Whether finished items are being cleared. */
    val clearing: Boolean = false,
    /** The rkey of the item the athlete asked to cancel, while they confirm. */
    val confirmingCancel: String? = null,
    /** The import the athlete asked to cancel the rest of, while they confirm. */
    val confirmingCancelRest: String? = null,
    /** Why the last action failed. */
    val error: UiError? = null,
    val eventSink: (UploadsEvent) -> Unit = {},
) : CircuitUiState {
    companion object {
        /** How many of an import's activities show before Show all. */
        const val IMPORT_PREVIEW = 20
    }
}

sealed interface UploadsEvent {
    data object Back : UploadsEvent

    data object Retry : UploadsEvent

    /** Asks to confirm cancelling the waiting or refused item at [rkey]. */
    data class Cancel(val rkey: String) : UploadsEvent

    /** Cancels the item at [rkey] after the athlete confirmed: its writes are dropped from the device, none of them sent. */
    data class ConfirmCancel(val rkey: String) : UploadsEvent

    /** Asks to confirm cancelling the waiting and refused activities of the import [batchId]. */
    data class CancelRest(val batchId: String) : UploadsEvent

    data class ConfirmCancelRest(val batchId: String) : UploadsEvent

    /** Dismisses either confirmation. */
    data object Dismiss : UploadsEvent

    /** Sends the refused change at [rkey] again. */
    data class SendAgain(val rkey: String) : UploadsEvent

    data object PauseImports : UploadsEvent

    data object ResumeImports : UploadsEvent

    /** Drops every finished item now. */
    data object ClearFinished : UploadsEvent

    /** Shows every activity of the import [batchId]. */
    data class ShowAll(val batchId: String) : UploadsEvent

    /** Opens the activity at [uri]. */
    data class OpenActivity(val uri: String) : UploadsEvent
}
