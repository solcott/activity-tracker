package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubPresenter
import com.slack.circuit.subcircuit.SubPresenterFactory
import com.slack.circuit.subcircuit.SubScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One screen the host shows: a place in a stack, or the single screen of a mode that has no stack (sign-in, a leave in progress). A UI
 * draws it from [state] and sends events to that state's sink (dev/presenter-host).
 */
@Stable
class HostRecord
@Suppress("LongParameterList") // What its stack knows about a screen when it makes the record; only the host's own code calls this.
internal constructor(
    /** Unique among the host's records for as long as the app runs: what a UI keys the screen's view on. */
    val key: String,
    /**
     * The screen itself, for Kotlin. A Swift UI reads [screenName] instead: Swift export hands Swift an interface-typed value through the
     * Swift class generated for its Kotlin class, and Xcode links only the generated modules the app's Swift refers to, so reading this for
     * a screen whose module no Swift code names stops the app.
     */
    val screen: Screen,
    /** The first screen of its stack, which draws no back button; also true for a screen with no stack. */
    val isRoot: Boolean,
    /** Whether the tabs show over this screen. False for a flow that takes the whole window (`TopLevelDestination.showsNavigation`). */
    val showsTabs: Boolean,
    internal val presenter: Presenter<*>,
    /** Where this record's sub-screens' presenters come from ([sub]). */
    private val presenters: Presenters,
    /**
     * The page a wide UI shows beside this screen until it opens one: Activity defaults, for Settings (ui/settings-layout). It is not in
     * the stack, so going back never returns to it; its presenter runs while the UI reports it visible, and what it retained is kept for as
     * long as this record is in its stack. Null for every screen that lays out no page beside it.
     */
    val paneDefault: HostRecord? = null,
    /**
     * Whether this screen is the first page shown beside Settings' list right now (`SettingsSplit`). The list is then where to go back to,
     * so a UI that draws the screen gives it no back button; the same screen shown on its own has one.
     */
    val paneRoot: StateFlow<Boolean> = MutableStateFlow(false),
    /** What this record's presenter navigates: its stack, or nothing for a screen with none. A screen it [embed]s navigates it too. */
    private val navigator: Navigator = Navigator.NoOp,
) {
    /**
     * The screen's latest state: null until its presenter first runs, and kept, unchanged, while the presenter is stopped (the screen is
     * covered or its tab isn't chosen), so whatever still draws the screen has something to draw.
     */
    val state: StateFlow<CircuitUiState?>
        field = MutableStateFlow<CircuitUiState?>(null)

    internal val visible = MutableStateFlow(false)

    /** The screen's class name, such as `LoginScreen`: what a Swift UI picks the screen's native view by. */
    val screenName: String = screen::class.simpleName.orEmpty()

    /**
     * Tells the host the UI is showing this screen although it isn't on top, as during a back swipe that reveals it, so its presenter runs
     * again before the swipe ends. The top screen of the chosen tab runs whatever is reported here.
     */
    fun setVisible(visible: Boolean) {
        this.visible.value = visible
    }

    internal fun publish(state: CircuitUiState) {
        this.state.value = state
    }

    /** The sub-screens a UI asked for and hasn't closed, which run inside this record's presenter while it runs. */
    internal val subs: StateFlow<ImmutableList<HostSubRecord>>
        field = MutableStateFlow<ImmutableList<HostSubRecord>>(persistentListOf())

    private var nextSub = 0

    /**
     * The sub-screen (SubCircuit) [screen] that this record's state embeds, run by the host as `SubCircuitContent` runs it in Compose: what
     * a native UI draws in place of `SubCircuitContent(screen, outerEventSink)`, sending the sub-screen's outer events to [outerEventSink]
     * as the shared UI does (dev/presenter-host). Asking again for an equal screen gives the same sub-record, which then sends to the
     * newest [outerEventSink]; a screen of the same class that isn't equal replaces the one held, starting afresh, as a changed screen
     * remounts `SubCircuitContent`.
     *
     * [screen] is an object because Swift has no type for `SubScreen`, whose generic interface isn't exported. It fails naming [screen]
     * when it isn't a sub-screen or no presenter is registered for it.
     */
    fun sub(screen: Any, outerEventSink: (SubCircuitOuterEvent) -> Unit): HostSubRecord {
        require(screen is SubScreen<*>) { "$screen is not a sub-screen" }
        val held = subs.value
        held
            .firstOrNull { it.screen == screen }
            ?.let { sub ->
                sub.outerEventSink = outerEventSink
                return sub
            }
        val sub = HostSubRecord("$key-sub-${nextSub++}", screen, presenters.createSub(screen), outerEventSink, this)
        subs.value = (held.filterNot { it.screen::class == screen::class } + sub).toImmutableList()
        return sub
    }

    internal fun drop(sub: HostSubRecord) {
        subs.value = subs.value.filterNot { it === sub }.toImmutableList()
    }

    /** The screens a UI asked this record to embed ([embed]), which run inside its presenter while it runs. */
    internal val embedded: StateFlow<ImmutableList<HostRecord>>
        field = MutableStateFlow<ImmutableList<HostRecord>>(persistentListOf())

    private var nextEmbedded = 0

    /**
     * The screen [screen] this record's page shows inside itself, as the shared UI places it with `CircuitContent` (the Search tab's
     * segment browse): a record of its own whose presenter runs inside this one's while it runs, so what it retains and saves is kept and
     * dropped with this record's, and whose navigation goes to this record's stack (dev/presenter-host). A native UI draws it from its
     * [state] as it draws any record. Asking again for an equal screen gives the same record.
     *
     * [screen] is an object for the same reason as [sub]'s. It fails naming [screen] when it isn't a screen or no presenter is registered
     * for it.
     */
    fun embed(screen: Any): HostRecord {
        require(screen is Screen) { "$screen is not a screen" }
        embedded.value
            .firstOrNull { it.screen == screen }
            ?.let {
                return it
            }
        val record =
            HostRecord(
                key = "$key-embedded-${nextEmbedded++}",
                screen = screen,
                isRoot = false,
                showsTabs = showsTabs,
                presenter = presenters.create(screen, navigator),
                presenters = presenters,
                navigator = navigator,
            )
        embedded.value = (embedded.value + record).toImmutableList()
        return record
    }
}

/**
 * The presenters of one scope, as the app's graph binds them: each factory answers for the screens it knows, and each sub-screen factory
 * for the sub-screens it knows. The signed-out scope has no sub-screens.
 */
internal class Presenters(
    private val factories: Set<Presenter.Factory>,
    private val subFactories: Set<SubPresenterFactory> = emptySet(),
) {
    /** The presenter for [screen], or an error naming it: a screen nothing presents is a screen left out of the graph. */
    fun create(screen: Screen, navigator: Navigator): Presenter<*> =
        factories.firstNotNullOfOrNull { it.create(screen, navigator, CircuitContext.EMPTY) }
            ?: error("No presenter is registered for $screen")

    /** The presenter for the sub-screen [screen], or an error naming it, as [create]. */
    fun createSub(screen: SubScreen<*>): SubPresenter<SubCircuitOuterEvent, SubCircuitUiState> {
        val presenter = subFactories.firstNotNullOfOrNull { it.create(screen) } ?: error("No presenter is registered for $screen")
        // A factory answers with the presenter of its screen's own outer event and state types, which the host passes through untouched.
        @Suppress("UNCHECKED_CAST")
        return presenter as SubPresenter<SubCircuitOuterEvent, SubCircuitUiState>
    }
}

/** Hands out [HostRecord.key]s. One per host, so no two of its records share a key. */
internal class RecordKeys {
    private var next = 0

    fun next(): String = "record-${next++}"
}
