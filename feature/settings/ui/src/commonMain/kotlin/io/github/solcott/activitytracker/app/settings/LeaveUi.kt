package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppListSection
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.export_data
import io.github.solcott.activitytracker.composeApp.resources.export_data_cancel
import io.github.solcott.activitytracker.composeApp.resources.leave_confirm
import io.github.solcott.activitytracker.composeApp.resources.leave_confirm_label
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_activities
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_follows
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_gear
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_interactions
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_profile
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_segments
import io.github.solcott.activitytracker.composeApp.resources.leave_deleted_settings
import io.github.solcott.activitytracker.composeApp.resources.leave_export_none
import io.github.solcott.activitytracker.composeApp.resources.leave_export_ready
import io.github.solcott.activitytracker.composeApp.resources.leave_export_running
import io.github.solcott.activitytracker.composeApp.resources.leave_export_save
import io.github.solcott.activitytracker.composeApp.resources.leave_intro
import io.github.solcott.activitytracker.composeApp.resources.leave_keep_open
import io.github.solcott.activitytracker.composeApp.resources.leave_section_confirm
import io.github.solcott.activitytracker.composeApp.resources.leave_section_deleted
import io.github.solcott.activitytracker.composeApp.resources.leave_section_device
import io.github.solcott.activitytracker.composeApp.resources.leave_section_export
import io.github.solcott.activitytracker.composeApp.resources.leave_section_places
import io.github.solcott.activitytracker.composeApp.resources.leave_section_stays
import io.github.solcott.activitytracker.composeApp.resources.leave_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.leave_stays_account
import io.github.solcott.activitytracker.composeApp.resources.leave_stays_other_apps
import io.github.solcott.activitytracker.composeApp.resources.leave_stopped
import io.github.solcott.activitytracker.composeApp.resources.leave_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Leave Activity Tracker (auth/leaving): what's deleted and what stays, the shared places that stay without the athlete's name, what this
 * device loses, the export and the grant, then a typed-handle confirmation; once the leave runs, only its progress.
 */
@CircuitInject(LeaveScreen::class, SessionScope::class)
@Composable
fun LeaveUi(state: LeaveState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val run = state.run
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.leave_title),
                // A leave under way can't be left: the app shows only it until it's done.
                onBack = if (state.canGoBack) ({ state.eventSink(LeaveEvent.Back) }) else null,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (run != null) Progress(run, state.eventSink) else Summary(state)
        }
    }
}

@Composable
private fun Summary(state: LeaveState) {
    val body = MaterialTheme.typography.bodyMedium
    Text(stringResource(Res.string.leave_intro, state.handle), Modifier.padding(horizontal = 16.dp), style = body)
    AppListSection(stringResource(Res.string.leave_section_deleted)) {
        Bullets(
            persistentListOf(
                stringResource(Res.string.leave_deleted_activities),
                stringResource(Res.string.leave_deleted_profile),
                stringResource(Res.string.leave_deleted_gear),
                stringResource(Res.string.leave_deleted_follows),
                stringResource(Res.string.leave_deleted_interactions),
                stringResource(Res.string.leave_deleted_segments),
                stringResource(Res.string.leave_deleted_settings),
            ),
        )
    }
    AppListSection(stringResource(Res.string.leave_section_stays)) {
        Bullets(
            persistentListOf(
                stringResource(Res.string.leave_stays_account, state.handle),
                stringResource(Res.string.leave_stays_other_apps),
            ),
        )
    }
    AppListSection(stringResource(Res.string.leave_section_places)) { Places(state.places) }
    DeviceWork(state)
    AppListSection(stringResource(Res.string.leave_section_export)) { Export(state) }
    AppListSection(stringResource(Res.string.leave_section_confirm)) { Confirm(state) }
}

/** Lines of text, each as a bullet. */
@Composable
private fun Bullets(lines: ImmutableList<String>) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Places(places: SharedPlacesSummary) {
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (places) {
            SharedPlacesSummary.Loading ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppProgressIndicator(small = true)
                    Text(LeaveTexts.places(places).first().string(), style = body)
                }
            is SharedPlacesSummary.Failed -> {
                ErrorText(places.error, style = body)
                LeaveTexts.places(places).forEach { Text(it.string(), style = body) }
            }
            is SharedPlacesSummary.Loaded -> LeaveTexts.places(places).forEach { Text(it.string(), style = body) }
        }
    }
}

@Composable
private fun DeviceWork(state: LeaveState) {
    if (!state.showsDeviceWork) return
    AppListSection(stringResource(Res.string.leave_section_device)) {
        Bullets(LeaveTexts.deviceLines(state).map { it.string() }.toImmutableList())
    }
}

@Composable
private fun Export(state: LeaveState) {
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.export) {
            LeaveExport.None -> {
                Text(stringResource(Res.string.leave_export_none), style = body)
                AppSecondaryButton(onClick = { state.eventSink(LeaveEvent.Export) }, enabled = !state.starting) {
                    Text(stringResource(Res.string.export_data))
                }
            }
            LeaveExport.Running -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppProgressIndicator(small = true)
                    Text(stringResource(Res.string.leave_export_running), style = body)
                }
                AppSecondaryButton(onClick = { state.eventSink(LeaveEvent.CancelExport) }) {
                    Text(stringResource(Res.string.export_data_cancel))
                }
            }
            LeaveExport.Ready -> {
                Text(stringResource(Res.string.leave_export_ready), style = body)
                AppButton(onClick = { state.eventSink(LeaveEvent.SaveExport) }) { Text(stringResource(Res.string.leave_export_save)) }
            }
        }
    }
}

@Composable
private fun Confirm(state: LeaveState) {
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.needsSignInAgain) {
            Text(stringResource(Res.string.leave_sign_in_again), style = body)
            AppButton(onClick = { state.eventSink(LeaveEvent.SignInAgain) }, enabled = !state.starting) {
                Text(stringResource(Res.string.action_sign_in_again))
            }
        } else {
            AppTextField(
                state = state.confirmHandle,
                label = stringResource(Res.string.leave_confirm_label, state.handle),
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = !state.starting,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.error?.let { ErrorText(it, style = body) }
        if (state.starting) {
            AppProgressIndicator(small = true)
        } else if (!state.needsSignInAgain) {
            AppButton(onClick = { state.eventSink(LeaveEvent.Leave) }, enabled = state.canLeave) {
                Text(stringResource(Res.string.leave_confirm))
            }
        }
        AppSecondaryButton(onClick = { state.eventSink(LeaveEvent.Back) }, enabled = !state.starting) {
            Text(stringResource(Res.string.action_cancel))
        }
    }
}

@Composable
private fun Progress(run: LeaveRun, eventSink: (LeaveEvent) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val error = run.error
        if (error == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppProgressIndicator(small = true)
                Text(LeaveTexts.step(run).string(), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            ErrorText(error, style = MaterialTheme.typography.titleMedium)
        }
        LeaveTexts.recordsDeleted(run)?.let { Text(it.string(), style = body) }
        LeaveTexts.paused(run)?.let { Text(it.string(), style = body) }
        if (error == null) {
            Text(stringResource(Res.string.leave_keep_open), style = body)
        } else {
            Text(stringResource(Res.string.leave_stopped), style = body)
            AppButton(onClick = { eventSink(LeaveEvent.Retry) }) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}
