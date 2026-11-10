package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.auth.LeaveStep
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlin.time.Instant

/**
 * Leave Activity Tracker (auth/leaving): what goes and what stays, then a typed-handle confirmation, then the leave's progress. Opened from
 * the Account page, or by the app shell with [resume] while a leave recorded on this device hasn't finished, which shows only its progress
 * and runs it again.
 */
@CircuitSerializable(SessionScope::class) data class LeaveScreen(val resume: Boolean = false) : SettingsPage

@Immutable
data class LeaveState(
    val handle: String,
    /** Where the athlete types [handle] to confirm. */
    val confirmHandle: TextFieldState,
    /** The athlete's segments and dive sites, counted by whether they stay for others. */
    val places: SharedPlacesSummary = SharedPlacesSummary.Loading,
    /** What this device holds that leaving discards; null while it's being read, or with nothing pending. */
    val pending: PendingWork? = null,
    /** A recording of this account's is in progress on this device, which leaving discards. */
    val recording: Boolean = false,
    val export: LeaveExport = LeaveExport.None,
    /** The sign-in predates the permission to delete spaces: the athlete must sign in again first. */
    val needsSignInAgain: Boolean = false,
    /** Why starting the leave (or the export) failed, until the next try: the PDS couldn't be reached, say. */
    val error: UiError? = null,
    /** Checking the PDS and recording the leave, after the athlete confirmed. */
    val starting: Boolean = false,
    /** The running leave, once it has started; then the screen shows only its progress. */
    val run: LeaveRun? = null,
    val eventSink: (LeaveEvent) -> Unit,
) : CircuitUiState {
    /** Whether the typed handle names this account: `@` and case don't matter. */
    val handleMatches: Boolean
        get() = confirmHandle.text.trim().removePrefix("@").toString().equals(handle, ignoreCase = true)

    /** Whether the confirm action is on. */
    val canLeave: Boolean
        get() = handleMatches && !needsSignInAgain && export != LeaveExport.Running && !starting && run == null
}

/** The athlete's own segments and dive sites, as leaving treats them (auth/leaving). */
@Immutable
sealed interface SharedPlacesSummary {
    data object Loading : SharedPlacesSummary

    /** [activeSegments] and [activeSites] others use stay, without the athlete's name; [otherSegments] and [otherSites] are deleted. */
    data class Loaded(val activeSegments: Int, val otherSegments: Int, val activeSites: Int, val otherSites: Int) : SharedPlacesSummary

    /** The counts couldn't be loaded; leaving is still allowed. */
    data class Failed(val error: UiError) : SharedPlacesSummary
}

/** The athlete's data export, as the leave screen treats it. */
enum class LeaveExport {
    /** None, or one that stopped: the screen offers to start one. */
    None,

    /** Running or paused: leaving waits for it, or for the athlete to cancel it. */
    Running,

    /** Ready and not yet saved: leaving deletes it, so the screen offers Save first. */
    Ready,
}

/** A leave under way: its [step] (null until it reports one, after a restart), records deleted so far, and what paused or stopped it. */
@Immutable
data class LeaveRun(
    val step: LeaveStep? = null,
    val recordsDeleted: Int = 0,
    /** Until when the PDS asked it to wait, while it does. */
    val pausedUntil: Instant? = null,
    /** The refusal that stopped it; Retry runs it again from where it stopped. */
    val error: UiError? = null,
)

sealed interface LeaveEvent {
    /** Cancels: nothing is deleted or discarded. */
    data object Back : LeaveEvent

    /** Starts a data export first, as the Account page's Export your data does. */
    data object Export : LeaveEvent

    /** Cancels the running export, so leaving can go ahead. */
    data object CancelExport : LeaveEvent

    /** Back to the Account page, where the ready export is saved. */
    data object SaveExport : LeaveEvent

    /** Ends the session to sign in again, for a grant that may delete spaces. */
    data object SignInAgain : LeaveEvent

    /** Confirms leaving, once the typed handle matches. */
    data object Leave : LeaveEvent

    /** Runs a stopped leave again from where it stopped. */
    data object Retry : LeaveEvent
}
