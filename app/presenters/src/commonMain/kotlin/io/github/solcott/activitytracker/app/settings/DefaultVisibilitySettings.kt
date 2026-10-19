package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.fieldsOf
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.VisibilityChoice

/**
 * The default visibilities in Settings: read again on opening (another device may have changed them), and saved as soon as one changes,
 * showing at once and reverting if the save fails. One save at a time: the pickers are disabled while one runs, and a change sent meanwhile
 * is dropped rather than shown unsaved.
 */
@Stable
class DefaultVisibilitySettings
internal constructor(private val defaults: ActivityDefaults, private val flights: SingleFlight, private val onError: (UiError?) -> Unit) {
    internal var shown by mutableStateOf<VisibilityChoice?>(null)

    val fields: VisibilityFields?
        get() = shown?.let { fieldsOf(it, defaults.canChoose, ::onEvent) }

    val saving: Boolean
        get() = flights.isRunning(KEY)

    fun onEvent(event: VisibilityEvent) {
        val shown = shown ?: return
        save(
            when (event) {
                is VisibilityEvent.ActivityChanged -> shown.withActivity(event.visibility)
                is VisibilityEvent.CategoryChanged -> shown.withCategory(event.category, event.visibility)
            }
        )
    }

    private fun save(choice: VisibilityChoice) {
        val before = shown ?: return
        if (choice == before || !defaults.canChoose || saving) return
        shown = choice
        onError(null)
        flights.launch(KEY) {
            runSuspendCatching { shown = defaults.save(choice) }
                .onFailure { e ->
                    shown = before
                    onError(uiErrorOf(UiError.Kind.SaveSettings, e))
                }
        }
    }

    private companion object {
        /** Every default saves as one. */
        val KEY = VisibilityEvent::class
    }
}

@Composable
internal fun rememberDefaultVisibilitySettings(
    defaults: ActivityDefaults,
    flights: SingleFlight,
    onError: (UiError?) -> Unit,
): DefaultVisibilitySettings {
    val settings = remember { DefaultVisibilitySettings(defaults, flights, onError) }
    LaunchedEffect(settings) { settings.shown = defaults.refresh() }
    return settings
}
