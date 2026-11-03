package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.di.SessionScope

@CircuitInject(ActivityDefaultsScreen::class, SessionScope::class)
@Composable
fun ActivityDefaultsPresenter(navigator: Navigator, defaults: ActivityDefaults): ActivityDefaultsState {
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val settings = rememberDefaultVisibilitySettings(defaults, flights) { error = it }
    return ActivityDefaultsState(settings.fields, settings.saving, error) { event ->
        when (event) {
            ActivityDefaultsEvent.Back -> navigator.pop()
        }
    }
}
