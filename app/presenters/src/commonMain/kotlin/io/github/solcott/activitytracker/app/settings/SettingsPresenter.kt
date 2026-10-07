package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitsRepository

@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsPresenter(navigator: Navigator, unitsRepository: UnitsRepository, auth: AuthRepository): SettingsState {
    // UnitsRepository updates optimistically, so the choice shows at once and reverts if the save fails.
    val units by unitsRepository.units.collectAsState()
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(SettingsEvent.UnitsChanged::class)

    return SettingsState(units, saving, error) { event ->
        when (event) {
            SettingsEvent.Back -> {
                navigator.pop()
            }

            is SettingsEvent.UnitsChanged -> {
                if (event.units != units && !saving) {
                    error = null
                    flights.launch(SettingsEvent.UnitsChanged::class) {
                        runSuspendCatching { unitsRepository.set(event.units) }
                            .onFailure { e -> error = UiError.of(UiError.Kind.SaveSettings, e) }
                    }
                }
            }

            SettingsEvent.OpenZones -> {
                navigator.goTo(TrainingZonesScreen)
            }

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            SettingsEvent.SignOut -> {
                flights.launch(SettingsEvent.SignOut) { auth.signOut() }
            }
        }
    }
}
