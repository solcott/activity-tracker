package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.export.ExportArchive

/**
 * The signed-in account (auth/account-page): who it is and where it's hosted, whether the sign-in grants what the app needs, where
 * account-level actions live (the PDS), and signing out. A page of Settings.
 */
@CircuitSerializable(SessionScope::class) data object AccountScreen : SettingsPage

@Immutable
data class AccountState(
    val handle: String,
    val did: String,
    /** The host name of the PDS the account lives on, which manages its password, two-factor sign-in, handle and deletion. */
    val pdsHost: String,
    val grant: GrantStatus,
    /** Set while the athlete is asked whether to end the session with work still pending on this device. */
    val confirming: EndingSession? = null,
    /** The session is ending: the page's actions are off until it's gone. */
    val ending: Boolean = false,
    /** The athlete's data export, if any (auth/data-export). */
    val export: DataExportStatus = DataExportStatus.None,
    /** Set while the athlete is asked whether they kept an export they shared (iOS's share sheet doesn't say). */
    val askingKeptShared: Boolean = false,
    /** Why starting, cancelling or saving the export failed, until the next try. */
    val exportError: UiError? = null,
    val eventSink: (AccountEvent) -> Unit,
) : CircuitUiState {
    /** Whether the Sign-in section offers Sign in again: the grant lacks something signing in again would pick up. */
    val offersSignInAgain: Boolean
        get() = grant is GrantStatus.Outdated
}

/** Where the athlete's data export stands, as the Account page shows it (auth/account-page). */
@Immutable
sealed interface DataExportStatus {
    /** No export: the page offers one. */
    data object None : DataExportStatus

    /**
     * Running: listing records ([records] so far), then fetching files ([filesDone] of [filesTotal]), then preparing each activity's file
     * ([activitiesDone] of [activitiesTotal]). Each step starts once the one before is done, so how far it's got says which it's on.
     */
    data class Running(val records: Int, val filesDone: Int, val filesTotal: Int, val activitiesDone: Int, val activitiesTotal: Int) :
        DataExportStatus {
        val phase: Phase
            get() =
                when {
                    activitiesDone > 0 || filesTotal > 0 && filesDone == filesTotal && activitiesTotal > 0 -> Phase.Activities
                    filesDone > 0 -> Phase.Files
                    else -> Phase.Records
                }

        enum class Phase {
            Records,
            Files,
            Activities,
        }
    }

    /**
     * Ready to save as [archive]; [problems] items were left out, [unsentChanges] weren't uploaded, and private data only if
     * [spacesIncluded].
     */
    data class Ready(val archive: ExportArchive, val problems: Int, val unsentChanges: Int, val spacesIncluded: Boolean) : DataExportStatus

    /** Paused for lack of space; [neededBytes] more free would let it go on, when known. */
    data class Paused(val neededBytes: Long?) : DataExportStatus

    /** Stopped: the server couldn't be reached. Trying again goes on from where it stopped. */
    data object Failed : DataExportStatus
}

/** The athlete chose to end the session, [signInAgain] or just sign out, and [pending] would be left behind. */
@Immutable data class EndingSession(val signInAgain: Boolean, val pending: PendingWork)

sealed interface AccountEvent {
    data object Back : AccountEvent

    data object SignOut : AccountEvent

    /** Ends the session and returns to the login screen with the handle filled in, to renew the grant. */
    data object SignInAgain : AccountEvent

    /**
     * Ends the session as [AccountState.confirming] asked: signing in again or just signing out. It carries the choice because the dialog
     * dismisses itself, sending [Dismiss], before its action runs.
     */
    data class Confirm(val signInAgain: Boolean) : AccountEvent

    data object Dismiss : AccountEvent

    /** Starts a data export, or goes on with a paused or failed one. */
    data object Export : AccountEvent

    /** Stops the export and deletes it. */
    data object CancelExport : AccountEvent

    /** The archive was written where the athlete chose: the app's copy goes. */
    data object ExportSaved : AccountEvent

    /** The archive went to the share sheet, which doesn't say whether it was kept: ask. */
    data object ExportShared : AccountEvent

    /** Writing the archive failed. */
    data object ExportSaveFailed : AccountEvent

    /** Answers [AccountState.askingKeptShared]: [kept] deletes the app's copy, else it stays to save later. */
    data class KeptShared(val kept: Boolean) : AccountEvent

    /** Opens Leave Activity Tracker (auth/leaving). */
    data object Leave : AccountEvent
}
