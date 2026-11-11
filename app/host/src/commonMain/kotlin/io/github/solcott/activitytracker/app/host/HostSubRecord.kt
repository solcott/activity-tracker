package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubPresenter
import com.slack.circuit.subcircuit.SubScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A sub-screen (SubCircuit) one of the host's records embeds, as a UI asked for it with [HostRecord.sub]: its presenter runs inside its
 * record's, while that one runs, and a UI draws it from [state] and sends its events to that state's sink (dev/presenter-host). What it
 * retained or saved is kept with its record's, so it comes back with the record and is forgotten when the record leaves its stack or this
 * is [close]d.
 */
@Stable
class HostSubRecord
internal constructor(
    /** Unique among the host's records and sub-records for as long as the app runs. */
    val key: String,
    internal val screen: SubScreen<*>,
    internal val presenter: SubPresenter<SubCircuitOuterEvent, SubCircuitUiState>,
    internal var outerEventSink: (SubCircuitOuterEvent) -> Unit,
    private val parent: HostRecord,
) {
    /** The sub-screen's latest state: null until its presenter first runs, and kept, unchanged, while it is stopped. */
    val state: StateFlow<SubCircuitUiState?>
        field = MutableStateFlow<SubCircuitUiState?>(null)

    /** The sub-screen's class name, such as `FeedFilterScreen`, as [HostRecord.screenName] is a screen's. */
    val screenName: String = screen::class.simpleName.orEmpty()

    /**
     * Stops this sub-screen and forgets what it kept, as a sheet that was dismissed: what a UI calls when it stops drawing the sub-screen
     * while its record still shows. Asking the record for an equal sub-screen afterwards starts a new one. Closing it again does nothing.
     */
    fun close() {
        parent.drop(this)
    }

    /** Forwards an outer event to the sink the UI gave last; stable, so the presenter isn't given a new lambda when the UI asks again. */
    internal val forward: (SubCircuitOuterEvent) -> Unit = { outerEventSink(it) }

    internal fun publish(state: SubCircuitUiState) {
        this.state.value = state
    }
}
