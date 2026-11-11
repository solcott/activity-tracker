package io.github.solcott.activitytracker.app.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.subcircuit.SubCircuitInject
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_done
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * The report sheet's content: a reason, an optional note (required for Other) and a word on what moderators may see, then thanks and, when
 * offered, Block. Its words and conditions are Kotlin's (`ReportTexts`, `ReportState`), shared with the native sheet.
 */
@SubCircuitInject(ReportScreen::class, SessionScope::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportUi(state: ReportState, modifier: Modifier = Modifier) {
    if (state.sent) {
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ReportTexts.thanksTitle.string(), style = MaterialTheme.typography.titleLarge)
            Text(ReportTexts.thanksMessage.string(), style = MaterialTheme.typography.bodyMedium)
            if (state.offerBlock) {
                AppSecondaryButton(onClick = { state.eventSink(ReportEvent.Block) }, modifier = Modifier.fillMaxWidth()) {
                    Text(ReportTexts.blockOffer(state.authorName).string())
                }
            }
            AppButton(onClick = { state.eventSink(ReportEvent.Close) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_done))
            }
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        AppSheetHeader(
            title = ReportTexts.title(state.authorName).string(),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { state.eventSink(ReportEvent.Close) },
            confirmLabel = ReportTexts.send.string(),
            onConfirm = { if (state.canSend) state.eventSink(ReportEvent.Send) },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(ReportTexts.reasonPrompt.string(), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportTexts.reasons.forEach { choice ->
                    AppFilterChip(
                        selected = state.chose(choice),
                        onClick = { state.eventSink(ReportEvent.PickReason(choice.reason)) },
                        enabled = !state.sending,
                        label = { Text(choice.label.string()) },
                    )
                }
            }
            AppTextField(
                state = state.note,
                modifier = Modifier.fillMaxWidth(),
                label = ReportTexts.note(state).string(),
                enabled = !state.sending,
                inputTransformation = { if (length > ReportTexts.noteLimit) revertAllChanges() },
            )
            state.error?.let { ErrorText(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                ReportTexts.privateNote.string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
