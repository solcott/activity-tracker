package io.github.solcott.activitytracker.app.login

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.location.ActivityRecorder

@CircuitInject(LoginScreen::class, AppScope::class)
@Inject
class LoginPresenter(
    private val coordinator: AuthCoordinator,
    private val auth: AuthRepository,
    private val recorder: ActivityRecorder,
) : Presenter<LoginState> {
    @Composable
    override fun present(): LoginState {
        val lastExpiry by auth.lastExpiry.collectAsState()
        // After an expiry the athlete signs back in to the same account, so its handle is filled in (auth/oauth-session).
        val handle = key(lastExpiry) { rememberTextFieldState(lastExpiry?.handle?.value.orEmpty()) }
        val recording by recorder.state.collectAsState()
        val expired = lastExpiry?.let { Expiry(recording = recording.inProgressFor(it.did.value)) }
        val status by coordinator.status.collectAsState()
        val flights = rememberSingleFlight()
        return LoginState(handle, status, expired) { event ->
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
