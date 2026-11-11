package io.github.solcott.activitytracker.app.uploads

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadState
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
    /**
     * The loaded queue, typed: Swift reads it here (dev/ios-swift-interop). Through [content]'s erased value [UploadQueueView], a
     * `:core-data` class no other contract signature names, has no Swift type in a release build, which exports no samples.
     */
    val queue: UploadQueueView?
        get() = content.data

    /** Whether Clear shows: once the queue has loaded. */
    val showsClear: Boolean
        get() = queue != null

    /** Whether Clear can be used: something is finished, and it isn't being cleared. */
    val canClear: Boolean
        get() = queue?.hasFinished == true && !clearing

    /** Whether Pause imports shows: an import has activities to upload, and the athlete hasn't paused imports. */
    val showsPauseImports: Boolean
        get() = !importsPaused && content.data?.imports?.any { it.live.isNotEmpty() } == true

    /** Whether the import being confirmed for Cancel rest is still saving, so its activities not saved yet would be dropped too. */
    val cancelRestSaving: Boolean
        get() = content.data?.imports?.find { it.id == confirmingCancelRest }?.save is ImportSaveStatus.Saving

    /**
     * [group]'s live activities as listed: the first [IMPORT_PREVIEW] until it's shown whole. A copy, for Swift (dev/ios-swift-interop).
     */
    fun shownLive(group: UploadImport): List<UploadItem> =
        if (group.id in expandedImports) group.live.toList() else group.live.take(IMPORT_PREVIEW)

    /** Whether [group] offers Show all: it has more live activities than show before it, and isn't shown whole yet. */
    fun showsShowAll(group: UploadImport): Boolean = group.id !in expandedImports && group.live.size > IMPORT_PREVIEW

    /** Whether [group] offers Cancel rest: it's still saving, or one of its live activities can be cancelled. */
    fun offersCancelRest(group: UploadImport): Boolean = group.save is ImportSaveStatus.Saving || group.live.any { it.cancellable }

    /** Whether the item at rkey [key], or the import [key]'s rest, is being cancelled or sent again. */
    fun isBusy(key: String): Boolean = key in busy

    /** Whether tapping [item] opens its activity: it's uploaded. */
    fun opens(item: UploadItem): Boolean = item.state == UploadState.Uploaded && item.uri != null

    /** Whether [item] offers Send again: the server refused it. */
    fun offersSendAgain(item: UploadItem): Boolean = item.uri != null && item.state == UploadState.Refused

    /** Whether [item] offers Cancel: nothing of it has been sent (data/upload-queue). */
    fun offersCancel(item: UploadItem): Boolean = item.cancellable

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
