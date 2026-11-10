package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuit.subcircuit.LocalSubCircuit
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.di.SessionGraph
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.HostRecord
import io.github.solcott.activitytracker.app.host.RootState
import io.github.solcott.activitytracker.app.host.startOnMain
import io.github.solcott.activitytracker.app.ui.HeldPhotoFetcher
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsNavRoot
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsPaneRoot
import io.github.solcott.activitytracker.app.ui.onRouteImageSession
import io.github.solcott.activitytracker.app.ui.theme.AppTheme
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.photoCid
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import platform.UIKit.UIViewController

/**
 * The iOS app's screens, run by the presenter host with no UI (dev/presenter-host). `iosApp`'s SwiftUI shell follows the host and draws
 * each screen: natively where it has a view, and otherwise with [ScreenViewController], the screen's shared Compose UI.
 *
 * Started by the first caller. Main thread only.
 */
internal object IosHost {
    private var graph: IosAppGraph? = null

    /** The signed-in account's graph, whose Circuit holds its screens' UIs; null when signed out. */
    var session: SessionGraph? = null
        private set

    fun start(graph: IosAppGraph): AppHost {
        if (this.graph != null) return graph.appHost
        this.graph = graph
        graph.appHost.startOnMain { oauth ->
            val sessionGraph = graph.createSessionGraph(oauth)
            session = sessionGraph
            // The image loader is app-wide; the photos it shows from this device's uploads are the signed-in session's.
            HeldPhotoFetcher.Factory.session.value = sessionGraph.feedRepository.heldPhotos
            sessionGraph.sessionHost
        }
        MainScope().launch {
            graph.appHost.root.collect { root ->
                when (root) {
                    RootState.Restoring -> Unit
                    is RootState.SignedOut -> {
                        session = null
                        HeldPhotoFetcher.Factory.session.value = null
                        // Signing out, or starting signed out, drops the feed's map images (feed/feed-route-map).
                        onRouteImageSession(null)
                    }
                    is RootState.SignedIn -> onRouteImageSession(root.session.did.value)
                }
            }
        }
        return graph.appHost
    }

    fun graph(): AppGraph = checkNotNull(graph) { "The host hasn't been started" }
}

/** Entry point for `:iosShell`: the app's presenter host, started the first time it is asked for. */
@Suppress("FunctionName") fun IosAppHost(fitDecoder: FitDecoder): AppHost = IosHost.start(appGraph(fitDecoder))

/**
 * Entry point for `:iosShell`: [record]'s screen drawn by the shared Compose UI, for a screen with no native view yet. Only the UI half:
 * the host runs the screen's presenter, and this draws the states it publishes.
 */
@Suppress("FunctionName")
fun ScreenViewController(record: HostRecord): UIViewController {
    val graph = IosHost.graph()
    val session = IosHost.session
    return ComposeUIViewController { Screen(record, graph, session) }
}

/**
 * Entry point for `:iosShell`: the bytes of the photo a `heldphoto:` URL names, from the signed-in session's uploads, as the shared UI's
 * image loader serves them ([HeldPhotoFetcher]); null when signed out or when the session doesn't hold it.
 */
@Suppress("FunctionName")
fun HeldPhotoBytes(url: String): ByteString? =
    if (HeldPhotos.isHeld(url)) IosHost.session?.feedRepository?.heldPhotos?.get(photoCid(url))?.bytes?.let(::ByteString) else null

/**
 * What `App` gives a screen's UI on the other platforms: the theme, Circuit's locals, and the signed-in session's units and sub-screens.
 */
@Composable
private fun Screen(record: HostRecord, graph: AppGraph, session: SessionGraph?) {
    UseHeldPhotoImageLoader()
    val circuit = session?.sessionCircuit?.circuit ?: graph.appCircuit.circuit
    val ui = remember(record, circuit) { uiFor(record, circuit) }
    val state by record.state.collectAsState()
    // The first page beside Settings' list draws no back button: the list is where to go back to (ui/settings-layout). The same view
    // controller draws the page when the window narrows and it has the screen to itself, so this follows the host.
    val paneRoot by record.paneRoot.collectAsState()
    AppTheme {
        CircuitCompositionLocals(circuit) {
            CompositionLocalProvider(LocalIsNavRoot provides record.isRoot, LocalIsPaneRoot provides paneRoot) {
                if (session == null) {
                    state?.let { ui.Content(it, Modifier) }
                } else {
                    val units by session.unitsRepository.units.collectAsState()
                    CompositionLocalProvider(LocalUnitSystem provides units, LocalSubCircuit provides session.subCircuit) {
                        state?.let { ui.Content(it, Modifier) }
                    }
                }
            }
        }
    }
}

/** The screen's Circuit UI, which takes whatever state its presenter publishes. */
@Suppress("UNCHECKED_CAST")
private fun uiFor(record: HostRecord, circuit: Circuit): Ui<CircuitUiState> =
    (circuit.ui(record.screen) ?: error("No UI is registered for ${record.screen}")) as Ui<CircuitUiState>
