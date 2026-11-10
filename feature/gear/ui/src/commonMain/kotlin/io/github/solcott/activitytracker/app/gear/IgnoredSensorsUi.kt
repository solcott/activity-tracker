package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.gear_ignored_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_ignored_sensors
import io.github.solcott.activitytracker.composeApp.resources.gear_unignore
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/** The devices the athlete marked as not gear, each with Un-ignore. */
@CircuitInject(IgnoredSensorsScreen::class, SessionScope::class)
@Composable
fun IgnoredSensorsUi(state: IgnoredSensorsState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(IgnoredSensorsEvent.Retry) }
    ListScreen(
        title = stringResource(Res.string.gear_ignored_sensors),
        onBack = { state.eventSink(IgnoredSensorsEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadIgnoredSensors,
        onRetry = retry,
        modifier = modifier,
    ) { sensors ->
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadIgnoredSensors, onRetry = retry) }
        state.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.padding(vertical = 8.dp)) } }
        if (sensors.isEmpty()) {
            item(key = "empty") {
                EmptyListText(stringResource(Res.string.gear_ignored_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(sensors, key = { it.id }) { sensor ->
            LinkedSensorRow(
                sensor,
                stringResource(Res.string.gear_unignore),
                LinkedSensorTexts.unignore(sensor).string(),
                { state.eventSink(IgnoredSensorsEvent.Unignore(sensor.id)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
