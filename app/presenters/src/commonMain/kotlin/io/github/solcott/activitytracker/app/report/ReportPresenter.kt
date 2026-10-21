package io.github.solcott.activitytracker.app.report

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubPresenter
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository

/** The report sheet: picks a reason and an optional note, sends them, then thanks the athlete and offers to block the author. */
@SubCircuitInject(ReportScreen::class, SessionScope::class)
@Inject
class ReportPresenter(private val screen: ReportScreen, private val reports: ReportRepository) :
    SubPresenter<ReportOuterEvent, ReportState> {
    @Composable
    override fun present(outerEventSink: (ReportOuterEvent) -> Unit): ReportState {
        var reason by rememberRetained { mutableStateOf<ReportReason?>(null) }
        val note = rememberRetained { TextFieldState() }
        var sent by rememberRetained { mutableStateOf(false) }
        var error by rememberRetained { mutableStateOf<UiError?>(null) }
        val flights = rememberSingleFlight()
        return ReportState(
            authorName = screen.authorName,
            reason = reason,
            note = note,
            sending = flights.isRunning(ReportEvent.Send),
            sent = sent,
            offerBlock = screen.offerBlock,
            error = error,
        ) { event ->
            when (event) {
                is ReportEvent.PickReason -> if (!sent) reason = event.reason
                ReportEvent.Send -> {
                    val picked = reason
                    // The repository refuses "other" without a note too; checked here so nothing is sent or reported.
                    val complete = picked != null && (!picked.requiresNote || note.text.isNotBlank())
                    if (complete && !sent) {
                        error = null
                        flights.launch(ReportEvent.Send) {
                            runSuspendCatching { reports.report(screen.target, checkNotNull(picked), note.text.toString()) }
                                .onSuccess { sent = true }
                                .onFailure { error = UiError.of(UiError.Kind.SendReport, it) }
                        }
                    }
                }
                ReportEvent.Block -> if (sent && screen.offerBlock) outerEventSink(ReportOuterEvent.Block(screen.target.author))
                ReportEvent.Close -> outerEventSink(ReportOuterEvent.Closed)
            }
        }
    }
}
