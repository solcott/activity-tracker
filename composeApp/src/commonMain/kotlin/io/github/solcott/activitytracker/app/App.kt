package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.navstack.ProvidedValues
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.subcircuit.LocalSubCircuit
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.di.SessionGraph
import io.github.solcott.activitytracker.app.di.circuits
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.nav.AppShell
import io.github.solcott.activitytracker.app.nav.appNavDecoration
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeftElsewhereScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.ui.HeldPhotoFetcher
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsNavRoot
import io.github.solcott.activitytracker.app.ui.onRouteImageSession
import io.github.solcott.activitytracker.app.ui.theme.AppTheme
import io.github.solcott.activitytracker.lexicon.Did

/**
 * Root: signed out → [LoginScreen] on the app-scoped Circuit; signed in → a fresh `SessionGraph` (keyed by DID) whose Circuit hosts the
 * navigable signed-in screens.
 */
@Composable
fun App(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    onRootPop: () -> Unit = {},
    /** Hears whether the signed-in athlete has an import uploading and not paused, so desktop and web ask before closing. */
    onImportsActive: (Boolean) -> Unit = {},
) {
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graph.authRepository.restore()
        restored = true
    }
    // A session whose refresh was refused ends the way a sign-out does, for as long as the app runs (auth/oauth-session).
    LaunchedEffect(Unit) { graph.authRepository.endExpiredSessions() }
    val session by graph.authRepository.session.collectAsState()
    // Signing out, or starting signed out, drops the feed's map images (feed/feed-route-map).
    LaunchedEffect(restored, session?.did) { if (restored) onRouteImageSession(session?.did?.value) }
    UseHeldPhotoImageLoader()

    AppTheme(modifier) {
        if (!restored) return@AppTheme
        val current = session
        if (current == null) {
            CircuitCompositionLocals(graph.circuits.appCircuit.circuit) { CircuitContent(LoginScreen) }
        } else {
            key(current.did) {
                val sessionGraph = remember { graph.createSessionGraph(current) }
                // The image loader is app-wide; the photos it shows from this device's uploads are the signed-in session's.
                DisposableEffect(sessionGraph) {
                    val held = sessionGraph.feedRepository.heldPhotos
                    HeldPhotoFetcher.Factory.session.value = held
                    onDispose { HeldPhotoFetcher.Factory.session.compareAndSet(held, null) }
                }
                // What the session shows, and when its background work runs, are decided by the runner both shells share
                // (dev/presenter-host): a leave recorded on this device, and one on another device, each take the app over
                // (auth/leaving).
                val runner = sessionGraph.sessionRunner
                LaunchedEffect(runner) { runner.run() }
                val mode by runner.mode.collectAsState()
                val units by sessionGraph.unitsRepository.units.collectAsState()
                CompositionLocalProvider(LocalUnitSystem provides units, LocalSubCircuit provides sessionGraph.circuits.subCircuit) {
                    CircuitCompositionLocals(sessionGraph.circuits.sessionCircuit.circuit) {
                        when (mode) {
                            SessionMode.Unknown -> Unit
                            // Only the leave, which resumes; nothing else runs, since it would write behind the leave.
                            SessionMode.Leaving ->
                                CompositionLocalProvider(LocalIsNavRoot provides true) { CircuitContent(LeaveScreen(resume = true)) }
                            // Nothing is sent until the athlete discards this device's work or rejoins, which brings the app back.
                            SessionMode.LeftElsewhere ->
                                CompositionLocalProvider(LocalIsNavRoot provides true) { CircuitContent(LeftElsewhereScreen) }
                            SessionMode.Open -> {
                                ImportsActive(sessionGraph, onImportsActive)
                                SessionContent(graph, sessionGraph, current.did, onRootPop)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tells desktop and web whether an import is uploading, so they ask before closing mid-import (data/upload-queue). */
@Composable
private fun ImportsActive(sessionGraph: SessionGraph, onImportsActive: (Boolean) -> Unit) {
    val importsActive by rememberUpdatedState(onImportsActive)
    LaunchedEffect(Unit) { sessionGraph.uploadQueue.importsActive.collect { importsActive(it) } }
    DisposableEffect(Unit) { onDispose { importsActive(false) } }
}

/**
 * Makes the app's image loader one that also serves `heldphoto:` URLs from the signed-in session's uploads ([HeldPhotoFetcher]). The loader
 * is app-wide and set once; every composition that draws the app's screens calls this, since whichever draws first sets it.
 */
@Composable
@ReadOnlyComposable
internal fun UseHeldPhotoImageLoader() {
    setSingletonImageLoaderFactory { context -> ImageLoader.Builder(context).components { add(HeldPhotoFetcher.Factory()) }.build() }
}

/** The signed-in app: the navigable screens in the shell, from the feed. */
@Composable
private fun SessionContent(graph: AppGraph, sessionGraph: SessionGraph, did: Did, onRootPop: () -> Unit) {
    val circuit = sessionGraph.circuits.sessionCircuit.circuit
    val backStack = rememberSaveableBackStack(root = FeedScreen)
    val navigator = rememberCircuitNavigator(backStack = backStack, onRootPop = { onRootPop() })
    // An unfinished recording of this account's, from before the app stopped, is offered back first, and one
    // still going since its session expired is shown as it is (record/gps-recording).
    LaunchedEffect(Unit) {
        graph.activityRecorder.recover(did.value)
        // After the first frame: navigating while the root screen is still being placed crashes Compose's insets on
        // iOS.
        withFrameNanos {}
        val recording = graph.activityRecorder.state.value
        if (recording.recovered || recording.inProgressFor(did.value)) navigator.goTo(RecordScreen)
    }
    // The recording notification was tapped.
    LaunchedEffect(Unit) {
        graph.recordScreenRequests.requests.collect {
            graph.recordScreenRequests.handled()
            if (backStack.topRecord?.screen != RecordScreen) navigator.goTo(RecordScreen)
        }
    }
    // The "export ready" notification was tapped (auth/data-export): Settings, then its Account page.
    LaunchedEffect(Unit) {
        graph.accountScreenRequests.requests.collect {
            graph.accountScreenRequests.handled()
            if (backStack.topRecord?.screen != AccountScreen) {
                navigator.goTo(SettingsScreen)
                navigator.goTo(AccountScreen)
            }
        }
    }
    AppShell(backStack, navigator, viewerDid = did.value) { contentModifier ->
        NavigableCircuitContent(
            navigator = navigator,
            navStack = backStack,
            modifier = contentModifier,
            providedValues = rootFlags(backStack),
            // Settings beside its page on a wide window; otherwise Android's predictive back, and the
            // default transition elsewhere.
            decoration = remember(circuit) { appNavDecoration(circuit) },
        )
    }
}

/** Tells each screen whether it's the root of the stack ([LocalIsNavRoot]), which drops its back button. */
private fun rootFlags(backStack: SaveableBackStack): Map<SaveableBackStack.Record, ProvidedValues> {
    val root = backStack.rootRecord
    return backStack.associateWith { record -> RootFlag(isRoot = record == root) }
}

/**
 * A class, not a `ProvidedValues { }` lambda: on Kotlin 2.5.0-Beta2 a lambda converted to a library's `fun interface` with a composable
 * method links against the method's signature from before the Compose compiler adds its parameters, so on iOS the first signed-in frame
 * died with `IrLinkageError: No function found for symbol …ProvidedValues.provideValues`. The JVM targets and the build don't notice.
 */
private class RootFlag(private val isRoot: Boolean) : ProvidedValues {
    @Composable override fun provideValues(): List<ProvidedValue<*>> = listOf(LocalIsNavRoot provides isRoot)
}
