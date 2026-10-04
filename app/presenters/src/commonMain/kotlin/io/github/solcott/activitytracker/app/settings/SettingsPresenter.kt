package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitsRepository
import kotlinx.coroutines.launch

@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsPresenter(navigator: Navigator, unitsRepository: UnitsRepository, auth: AuthRepository): SettingsState {
    // UnitsRepository updates optimistically, so the choice shows at once and reverts if the save fails.
    val units by unitsRepository.units.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    return SettingsState(units, saving, error) { event ->
        when (event) {
            SettingsEvent.Back -> {
                navigator.pop()
            }

            is SettingsEvent.UnitsChanged -> {
                if (event.units != units && !saving) {
                    saving = true
                    error = null
                    scope.launch {
                        runSuspendCatching { unitsRepository.set(event.units) }
                            .onFailure { e -> error = UiError.of(UiError.Kind.SaveSettings, e) }
                        saving = false
                    }
                }
            }

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            SettingsEvent.SignOut -> {
                scope.launch { auth.signOut() }
            }
        }
    }
}
