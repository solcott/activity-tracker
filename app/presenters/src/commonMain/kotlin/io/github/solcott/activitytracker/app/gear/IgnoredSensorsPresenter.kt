package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearDefaults
import io.github.solcott.activitytracker.data.gear.GearLinkEdit
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/**
 * The devices the athlete marked as not gear, read afresh from their `gear.autoAssign` on opening, on Retry and after each un-ignore, which
 * saves at once; the next import naming the device offers it again.
 */
@CircuitInject(IgnoredSensorsScreen::class, SessionScope::class)
@Composable
fun IgnoredSensorsPresenter(navigator: Navigator, defaults: GearDefaults): IgnoredSensorsState {
    // Bumped on Retry and after each un-ignore: the list reads again, staying on screen meanwhile.
    var retryKey by remember { mutableIntStateOf(0) }
    val content =
        produceRetainedContentState<ImmutableList<LinkedSensor>>(persistentListOf(), retryKey) {
            defaults.recordOutcome().map { outcome -> outcome.mapData(::ignoredOf) }
        }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    return IgnoredSensorsState(content, error) { event ->
        when (event) {
            IgnoredSensorsEvent.Back -> navigator.pop()
            IgnoredSensorsEvent.Retry -> retryKey++
            is IgnoredSensorsEvent.Unignore -> {
                val key = defaults.current.value?.ignored.orEmpty().firstOrNull { it.id == event.id } ?: return@IgnoredSensorsState
                error = null
                flights.launch(event) {
                    runSuspendCatching { defaults.editLinks(GearLinkEdit(unignore = listOf(key))) }
                        .onSuccess { retryKey++ }
                        .onFailure { error = uiErrorOf(UiError.Kind.SaveGearLinks, it) }
                }
            }
        }
    }
}

private fun ignoredOf(record: GearAutoAssignRecord): ImmutableList<LinkedSensor> =
    record.ignored
        .orEmpty()
        .map { LinkedSensor(it.id, it.manufacturer, it.model, it.serialNumber?.takeLast(SERIAL_TAIL), it.weak) }
        .toImmutableList()

private const val SERIAL_TAIL = 4
