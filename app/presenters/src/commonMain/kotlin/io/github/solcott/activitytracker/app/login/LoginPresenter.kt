package io.github.solcott.activitytracker.app.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import kotlinx.coroutines.launch

@CircuitInject(LoginScreen::class, AppScope::class)
@Inject
class LoginPresenter(private val coordinator: AuthCoordinator) : Presenter<LoginState> {
    @Composable
    override fun present(): LoginState {
        var handle by rememberSaveable { mutableStateOf("") }
        val status by coordinator.status.collectAsState()
        val scope = rememberCoroutineScope()
        return LoginState(handle, status) { event ->
            when (event) {
                is LoginEvent.HandleChanged -> handle = event.handle.trim()
                LoginEvent.Submit -> if (handle.isNotBlank()) scope.launch { coordinator.signIn(handle.lowercase()) }
                LoginEvent.Cancel -> coordinator.cancelled()
            }
        }
    }
}
