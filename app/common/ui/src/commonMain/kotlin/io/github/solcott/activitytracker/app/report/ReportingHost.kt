package io.github.solcott.activitytracker.app.report

import androidx.compose.runtime.Composable
import com.slack.circuit.subcircuit.SubCircuitContent
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

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
        val block = ReportTexts.block.string()
        AppAlertDialog(
            title = ReportTexts.blockConfirmTitle(offer).string(),
            message = ReportTexts.blockConfirmMessage.string(),
            actions =
                persistentListOf(AppDialogAction(block, destructive = true) { reporting.eventSink(ReportingEvent.ConfirmBlock(true)) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { reporting.eventSink(ReportingEvent.ConfirmBlock(false)) },
        )
    }
}
