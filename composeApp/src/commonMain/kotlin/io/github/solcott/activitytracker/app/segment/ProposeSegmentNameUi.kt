package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.subcircuit.SubCircuitInject
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.segment_name_disclosure
import io.github.solcott.activitytracker.composeApp.resources.segment_name_edit_warning
import io.github.solcott.activitytracker.composeApp.resources.segment_name_field
import io.github.solcott.activitytracker.composeApp.resources.segment_name_invalid
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_edit_title
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_propose_title
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_save
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_send
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * The name sheet's content: the name, and before it's sent what proposing shows others (a new proposal) or that saving clears the votes (an
 * edit).
 */
@SubCircuitInject(ProposeSegmentNameScreen::class, SessionScope::class)
@Composable
fun ProposeSegmentNameUi(state: ProposeSegmentNameState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AppSheetHeader(
            title =
                stringResource(
                    if (state.editing) Res.string.segment_name_sheet_edit_title else Res.string.segment_name_sheet_propose_title
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { state.eventSink(ProposeSegmentNameEvent.Cancel) },
            confirmLabel = stringResource(if (state.editing) Res.string.segment_name_sheet_save else Res.string.segment_name_sheet_send),
            onConfirm = { if (state.canSend) state.eventSink(ProposeSegmentNameEvent.Send) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (state.editing) Res.string.segment_name_edit_warning else Res.string.segment_name_disclosure),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.segment_name_field),
                enabled = !state.sending,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            if (state.name.text.isNotEmpty() && !state.valid) {
                Text(
                    stringResource(Res.string.segment_name_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
