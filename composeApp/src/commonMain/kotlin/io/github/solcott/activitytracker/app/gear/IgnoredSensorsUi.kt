package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.gear_ignored_empty
import io.github.solcott.activitytracker.composeApp.resources.gear_ignored_sensors
import io.github.solcott.activitytracker.composeApp.resources.gear_unignore
import io.github.solcott.activitytracker.composeApp.resources.gear_unignore_named
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/** The devices the athlete marked as not gear, each with Un-ignore. */
@CircuitInject(IgnoredSensorsScreen::class, SessionScope::class)
@Composable
fun IgnoredSensorsUi(state: IgnoredSensorsState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.gear_ignored_sensors),
                onBack = { state.eventSink(IgnoredSensorsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(IgnoredSensorsEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadIgnoredSensors, retry, contentPadding = padding) { sensors ->
            val listState = rememberLazyListState()
            ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                    item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadIgnoredSensors, onRetry = retry) }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(errorText(error), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (sensors.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(Res.string.gear_ignored_empty),
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(sensors, key = { it.id }) { sensor ->
                        LinkedSensorRow(
                            sensor,
                            stringResource(Res.string.gear_unignore),
                            stringResource(Res.string.gear_unignore_named, deviceName(sensor.manufacturer, sensor.model)),
                            { state.eventSink(IgnoredSensorsEvent.Unignore(sensor.id)) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
