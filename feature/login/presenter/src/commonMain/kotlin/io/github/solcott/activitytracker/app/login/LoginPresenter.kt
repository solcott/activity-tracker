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
import io.github.solcott.activitytracker.data.auth.EndedSession
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
        val lastEnd by auth.lastEnd.collectAsState()
        // After an expiry or a chosen Sign in again the athlete signs back in to the same account, so its handle is filled in
        // (auth/oauth-session). After leaving, signing in again starts the account afresh, so nothing is filled in (auth/leaving).
        val left = lastEnd?.takeIf { it.reason == EndedSession.Reason.Left }
        val handle = key(lastEnd) { rememberTextFieldState(if (left != null) "" else lastEnd?.handle?.value.orEmpty()) }
        val recording by recorder.state.collectAsState()
        val expired =
            lastEnd?.takeIf { it.reason == EndedSession.Reason.Expired }?.let { Expiry(recording = recording.inProgressFor(it.did.value)) }
        val signingInAgain = lastEnd?.reason == EndedSession.Reason.SignInAgain
        val status by coordinator.status.collectAsState()
        val flights = rememberSingleFlight()
        return LoginState(handle, status, expired, signingInAgain, left?.handle?.value) { event ->
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
