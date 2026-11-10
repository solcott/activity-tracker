package io.github.solcott.activitytracker.app.login

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import dev.zacsweers.metro.AppScope
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.textfield.TextFieldState

@CircuitSerializable(AppScope::class) data object LoginScreen : Screen

@Immutable
data class LoginState(
    val handle: TextFieldState,
    val status: Status,
    /** Set when the last session ended because it expired, rather than the athlete signing out. */
    val expired: Expiry? = null,
    /** Set when the athlete chose Sign in again on the Account page: the handle is filled in and the screen says why. */
    val signingInAgain: Boolean = false,
    /**
     * The handle of the account that just left Activity Tracker (auth/leaving): the screen says so, and its atproto account is unchanged.
     */
    val leftHandle: String? = null,
    val eventSink: (LoginEvent) -> Unit,
) : CircuitUiState {
    val busy: Boolean
        get() = status is Status.Starting || status is Status.Completing
}

/** The last session expired; [recording] while that account's recording is still going. */
@Immutable data class Expiry(val recording: Boolean)

sealed interface LoginEvent {
    data object Submit : LoginEvent

    data object Cancel : LoginEvent
}
