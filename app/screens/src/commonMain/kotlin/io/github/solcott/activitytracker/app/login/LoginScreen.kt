package io.github.solcott.activitytracker.app.login

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import dev.zacsweers.metro.AppScope
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status

@CircuitSerializable(AppScope::class) data object LoginScreen : Screen

data class LoginState(
    val handle: String,
    val status: Status,
    val eventSink: (LoginEvent) -> Unit,
) : CircuitUiState {
    val busy: Boolean
        get() = status is Status.Starting || status is Status.Completing
}

sealed interface LoginEvent {
    data class HandleChanged(val handle: String) : LoginEvent

    data object Submit : LoginEvent

    data object Cancel : LoginEvent
}
