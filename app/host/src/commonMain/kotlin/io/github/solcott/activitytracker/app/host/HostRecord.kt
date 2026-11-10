package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One screen the host shows: a place in a stack, or the single screen of a mode that has no stack (sign-in, a leave in progress). A UI
 * draws it from [state] and sends events to that state's sink (dev/presenter-host).
 */
@Stable
class HostRecord
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
}

/** The presenters of one scope, as the app's graph binds them: each factory answers for the screens it knows. */
internal class Presenters(private val factories: Set<Presenter.Factory>) {
    /** The presenter for [screen], or an error naming it: a screen nothing presents is a screen left out of the graph. */
    fun create(screen: Screen, navigator: Navigator): Presenter<*> =
        factories.firstNotNullOfOrNull { it.create(screen, navigator, CircuitContext.EMPTY) }
            ?: error("No presenter is registered for $screen")
}

/** Hands out [HostRecord.key]s. One per host, so no two of its records share a key. */
internal class RecordKeys {
    private var next = 0

    fun next(): String = "record-${next++}"
}
