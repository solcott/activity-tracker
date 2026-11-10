package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.DetailVisibilityPicker
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.settings_activity_defaults
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_activity
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/** Who new activities and each detail category start out shown to; the categories' choices are capped at the activity's. */
@CircuitInject(ActivityDefaultsScreen::class, SessionScope::class)
@Composable
fun ActivityDefaultsUi(state: ActivityDefaultsState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.settings_activity_defaults),
                onBack = { state.eventSink(ActivityDefaultsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.defaults?.let { defaults ->
                val enabled = state.canChange
                Text(stringResource(Res.string.settings_defaults_activity), style = MaterialTheme.typography.labelLarge)
                VisibilityPicker(
                    defaults.activity,
                    onSelect = { defaults.eventSink(VisibilityEvent.ActivityChanged(it)) },
                    approvesFollowers = defaults.approvesFollowers,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    options = defaults.activityOptions,
                )
                DetailVisibilityPicker(defaults, modifier = Modifier.fillMaxWidth(), enabled = enabled)
                Text(SettingsTexts.defaultsNote(defaults, state.saving).string(), style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { ErrorText(it) }
        }
    }
}
