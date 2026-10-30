package io.github.solcott.activitytracker.app.segment

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
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * The name sheet (segments/segment-names): proposes a new name for the segment, or edits the athlete's own open proposal, queues it through
 * [SegmentNames] and closes. A blank, over-long or unchanged name isn't sent.
 */
@SubCircuitInject(ProposeSegmentNameScreen::class, SessionScope::class)
@Inject
class ProposeSegmentNamePresenter(private val screen: ProposeSegmentNameScreen, private val names: SegmentNames) :
    SubPresenter<ProposeSegmentNameOuterEvent, ProposeSegmentNameState> {
    @Composable
    override fun present(outerEventSink: (ProposeSegmentNameOuterEvent) -> Unit): ProposeSegmentNameState {
        val name = rememberRetained { TextFieldState(screen.proposalName.orEmpty()) }
        var error by rememberRetained { mutableStateOf<UiError?>(null) }
        val flights = rememberSingleFlight()
        val state =
            ProposeSegmentNameState(
                name = name,
                editing = screen.proposal != null,
                unchangedName = screen.proposalName ?: screen.currentName,
                sending = flights.isRunning(ProposeSegmentNameEvent.Send),
                error = error,
            ) {}
        return state.copy { event ->
            when (event) {
                ProposeSegmentNameEvent.Send -> {
                    if (state.valid) {
                        error = null
                        val text = name.text.trim().toString()
                        flights.launch(ProposeSegmentNameEvent.Send) {
                            runSuspendCatching {
                                val segment = AtUri(screen.segment)
                                val proposal = screen.proposal
                                if (proposal == null) names.propose(segment, text) else names.edit(segment, AtUri(proposal), text)
                            }
                                .onSuccess { outerEventSink(ProposeSegmentNameOuterEvent.Closed) }
                                .onFailure { error = UiError.of(UiError.Kind.ProposeSegmentName, it) }
                        }
                    }
                }
                ProposeSegmentNameEvent.Cancel -> outerEventSink(ProposeSegmentNameOuterEvent.Closed)
            }
        }
    }
}
