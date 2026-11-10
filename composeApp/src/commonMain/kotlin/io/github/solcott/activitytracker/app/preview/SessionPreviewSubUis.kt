package io.github.solcott.activitytracker.app.preview

import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import com.slack.circuit.subcircuit.SubUi
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterState
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterUi
import io.github.solcott.activitytracker.app.names.ProposePlaceNameScreen
import io.github.solcott.activitytracker.app.names.ProposePlaceNameState
import io.github.solcott.activitytracker.app.names.ProposePlaceNameUi
import io.github.solcott.activitytracker.app.report.ReportScreen
import io.github.solcott.activitytracker.app.report.ReportState
import io.github.solcott.activitytracker.app.report.ReportUi
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.preview.PreviewSubUis
import io.github.solcott.activitytracker.textfield.textFieldStateOf

/**
 * The session's sub-screens with their real UIs, for [AppPreview]: the feed filter, reporting and segment name proposals. A new sub-screen
 * registers here too. Production code, like [AppPreview]: `:androidApp`'s screenshot tests use it.
 */
object SessionPreviewSubUis : PreviewSubUis {
    override fun ui(screen: SubScreen<*>): SubUi<*>? =
        when (screen) {
            is FeedFilterScreen -> SubUi<FeedFilterState> { state, modifier -> FeedFilterUi(state, modifier) }
            is ReportScreen -> SubUi<ReportState> { state, modifier -> ReportUi(state, modifier) }
            is ProposePlaceNameScreen -> SubUi<ProposePlaceNameState> { state, modifier -> ProposePlaceNameUi(state, modifier) }
            else -> null
        }

    override fun defaultState(screen: SubScreen<*>): SubCircuitUiState? =
        when (screen) {
            is FeedFilterScreen -> FeedFilterState(screen.initial, typePicker = null, customRange = null, eventSink = {})
            is ReportScreen ->
                ReportState(
                    authorName = screen.authorName,
                    reason = null,
                    note = textFieldStateOf(),
                    sending = false,
                    sent = false,
                    offerBlock = screen.offerBlock,
                    error = null,
                    eventSink = {},
                )
            else -> null
        }
}
