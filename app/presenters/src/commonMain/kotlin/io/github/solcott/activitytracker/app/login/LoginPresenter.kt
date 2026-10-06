package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.data.auth.AuthCoordinator

@CircuitInject(LoginScreen::class, AppScope::class)
@Inject
class LoginPresenter(private val coordinator: AuthCoordinator) : Presenter<LoginState> {
    @Composable
    override fun present(): LoginState {
        val handle = rememberTextFieldState()
        val status by coordinator.status.collectAsState()
        val flights = rememberSingleFlight()
        return LoginState(handle, status) { event ->
            when (event) {
                LoginEvent.Submit -> {
                    val typed = handle.text.trim()
                    if (typed.isNotEmpty()) flights.launch(LoginEvent.Submit) { coordinator.signIn(typed.toString().lowercase()) }
                }
                LoginEvent.Cancel -> coordinator.cancelled()
            }
        }
    }
}
