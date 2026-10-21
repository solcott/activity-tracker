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
import com.slack.circuit.subcircuit.SubCircuitContent
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubUi
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_block
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_done
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_message
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_title
import io.github.solcott.activitytracker.composeApp.resources.report_block_offer
import io.github.solcott.activitytracker.composeApp.resources.report_note
import io.github.solcott.activitytracker.composeApp.resources.report_note_required
import io.github.solcott.activitytracker.composeApp.resources.report_private_note
import io.github.solcott.activitytracker.composeApp.resources.report_reason_harassment
import io.github.solcott.activitytracker.composeApp.resources.report_reason_misleading
import io.github.solcott.activitytracker.composeApp.resources.report_reason_other
import io.github.solcott.activitytracker.composeApp.resources.report_reason_prompt
import io.github.solcott.activitytracker.composeApp.resources.report_reason_sexual
import io.github.solcott.activitytracker.composeApp.resources.report_reason_spam
import io.github.solcott.activitytracker.composeApp.resources.report_reason_violence
import io.github.solcott.activitytracker.composeApp.resources.report_send
import io.github.solcott.activitytracker.composeApp.resources.report_thanks_message
import io.github.solcott.activitytracker.composeApp.resources.report_thanks_title
import io.github.solcott.activitytracker.composeApp.resources.report_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * The report sheet's content: a reason, an optional note (required for Other) and a word on what moderators may see, then thanks and, when
 * offered, Block.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportUi(state: ReportState, modifier: Modifier = Modifier) {
    if (state.sent) {
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.report_thanks_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.report_thanks_message), style = MaterialTheme.typography.bodyMedium)
            if (state.offerBlock) {
                AppSecondaryButton(onClick = { state.eventSink(ReportEvent.Block) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.report_block_offer, state.authorName))
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
            title = stringResource(Res.string.report_title, state.authorName),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { state.eventSink(ReportEvent.Close) },
            confirmLabel = stringResource(Res.string.report_send),
            onConfirm = { if (state.canSend) state.eventSink(ReportEvent.Send) },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.report_reason_prompt), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportReason.entries.forEach { reason ->
                    AppFilterChip(
                        selected = state.reason == reason,
                        onClick = { state.eventSink(ReportEvent.PickReason(reason)) },
                        enabled = !state.sending,
                        label = { Text(reasonLabel(reason)) },
                    )
                }
            }
            AppTextField(
                state = state.note,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(if (state.reason?.requiresNote == true) Res.string.report_note_required else Res.string.report_note),
                enabled = !state.sending,
                inputTransformation = { if (length > ReportRepository.MAX_NOTE_LENGTH) revertAllChanges() },
            )
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                stringResource(Res.string.report_private_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Registers [ReportUi]. A class rather than the annotated function: that factory crashes Kotlin/Wasm (see compose-screens.md). */
@SubCircuitInject(ReportScreen::class, SessionScope::class)
@Inject
class ReportSubUi : SubUi<ReportState> {
    @Composable override fun Content(state: ReportState, modifier: Modifier) = ReportUi(state, modifier)
}

/** A host's [Reporting]: the report sheet while it's open, and the usual Block confirmation once its offer is taken. */
@Composable
fun ReportingHost(reporting: Reporting) {
    reporting.sheet?.let { sheet ->
        AppBottomSheet(
            onDismiss = { reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Closed)) },
            sheetState = rememberAppSheetState(),
        ) {
            SubCircuitContent(sheet, outerEventSink = { reporting.eventSink(ReportingEvent.Sheet(it)) })
        }
    }
    reporting.confirmBlock?.let { offer ->
        val block = stringResource(Res.string.action_block)
        AppAlertDialog(
            title = stringResource(Res.string.block_confirm_title, offer.name),
            message = stringResource(Res.string.block_confirm_message),
            actions =
                persistentListOf(AppDialogAction(block, destructive = true) { reporting.eventSink(ReportingEvent.ConfirmBlock(true)) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { reporting.eventSink(ReportingEvent.ConfirmBlock(false)) },
        )
    }
}

@Composable
private fun reasonLabel(reason: ReportReason): String =
    stringResource(
        when (reason) {
            ReportReason.SPAM -> Res.string.report_reason_spam
            ReportReason.SEXUAL -> Res.string.report_reason_sexual
            ReportReason.VIOLENCE -> Res.string.report_reason_violence
            ReportReason.HARASSMENT -> Res.string.report_reason_harassment
            ReportReason.MISLEADING -> Res.string.report_reason_misleading
            ReportReason.OTHER -> Res.string.report_reason_other
        }
    )
