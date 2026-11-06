package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_changes
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_choice
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_discard
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_export
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_import
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_intro
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_recording
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_rejoin
import io.github.solcott.activitytracker.composeApp.resources.left_elsewhere_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The athlete left Activity Tracker on another device, and this one holds work for the account: discard it, or rejoin (auth/leaving). */
@CircuitInject(LeftElsewhereScreen::class, SessionScope::class)
@Composable
fun LeftElsewhereUi(state: LeftElsewhereState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = stringResource(Res.string.left_elsewhere_title), scrollBehavior = scrollBehavior) },
    ) { padding ->
        ReadableScrollColumn(padding) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.checking) {
                    AppProgressIndicator()
                    return@Column
                }
                val body = MaterialTheme.typography.bodyMedium
                Text(stringResource(Res.string.left_elsewhere_intro, state.handle), style = body)
                if (state.changes > 0) {
                    Text(pluralStringResource(Res.plurals.left_elsewhere_changes, state.changes, state.changes), style = body)
                }
                if (state.importSaving) Text(stringResource(Res.string.left_elsewhere_import), style = body)
                if (state.exportPending) Text(stringResource(Res.string.left_elsewhere_export), style = body)
                if (state.recording) Text(stringResource(Res.string.left_elsewhere_recording), style = body)
                Text(stringResource(Res.string.left_elsewhere_choice), style = body)
                state.error?.let { ErrorText(it, style = body) }
                if (state.busy) AppProgressIndicator(small = true)
                AppButton(onClick = { state.eventSink(LeftElsewhereEvent.Rejoin) }, enabled = !state.busy) {
                    Text(stringResource(Res.string.left_elsewhere_rejoin))
                }
                AppSecondaryButton(onClick = { state.eventSink(LeftElsewhereEvent.Discard) }, enabled = !state.busy) {
                    Text(stringResource(Res.string.left_elsewhere_discard))
                }
            }
        }
    }
}
