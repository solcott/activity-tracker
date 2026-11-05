package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.di.SessionScope

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
    val eventSink: (AccountEvent) -> Unit,
) : CircuitUiState

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
}
