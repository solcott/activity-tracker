package io.github.solcott.activitytracker.app.names

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
 * The name sheet (segments/segment-names): proposes a new name for the place, or edits the athlete's own open proposal, queues it through
 * [SegmentNames] and closes. A blank, over-long or unchanged name isn't sent.
 */
@SubCircuitInject(ProposePlaceNameScreen::class, SessionScope::class)
@Inject
class ProposePlaceNamePresenter(private val screen: ProposePlaceNameScreen, private val names: SegmentNames) :
    SubPresenter<ProposePlaceNameOuterEvent, ProposePlaceNameState> {
    @Composable
    override fun present(outerEventSink: (ProposePlaceNameOuterEvent) -> Unit): ProposePlaceNameState {
        val name = rememberRetained { TextFieldState(screen.proposalName.orEmpty()) }
        var error by rememberRetained { mutableStateOf<UiError?>(null) }
        val flights = rememberSingleFlight()
        val state =
            ProposePlaceNameState(
                name = name,
                editing = screen.proposal != null,
                unchangedName = screen.proposalName ?: screen.currentName,
                sending = flights.isRunning(ProposePlaceNameEvent.Send),
                error = error,
            ) {}
        return state.copy { event ->
            when (event) {
                ProposePlaceNameEvent.Send -> {
                    if (state.valid) {
                        error = null
                        val text = name.text.trim().toString()
                        flights.launch(ProposePlaceNameEvent.Send) {
                            runSuspendCatching {
                                val place = AtUri(screen.place)
                                val proposal = screen.proposal
                                if (proposal == null) names.propose(place, text) else names.edit(place, AtUri(proposal), text)
                            }
                                .onSuccess { outerEventSink(ProposePlaceNameOuterEvent.Closed) }
                                .onFailure { error = UiError.of(UiError.Kind.ProposeSegmentName, it) }
                        }
                    }
                }
                ProposePlaceNameEvent.Cancel -> outerEventSink(ProposePlaceNameOuterEvent.Closed)
            }
        }
    }
}
