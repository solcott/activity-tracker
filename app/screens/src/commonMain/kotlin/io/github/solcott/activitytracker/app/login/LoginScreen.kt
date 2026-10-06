package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import dev.zacsweers.metro.AppScope
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status

@CircuitSerializable(AppScope::class) data object LoginScreen : Screen

@Immutable
data class LoginState(
    val handle: TextFieldState,
    val status: Status,
    val eventSink: (LoginEvent) -> Unit,
) : CircuitUiState {
    val busy: Boolean
        get() = status is Status.Starting || status is Status.Completing
}

sealed interface LoginEvent {
    data object Submit : LoginEvent

    data object Cancel : LoginEvent
}
