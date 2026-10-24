package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.nav.AppShell
import io.github.solcott.activitytracker.app.ui.HeldPhotoFetcher
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsNavRoot
import io.github.solcott.activitytracker.app.ui.theme.AppTheme

/**
 * Root: signed out → [LoginScreen] on the app-scoped Circuit; signed in → a fresh `SessionGraph` (keyed by DID) whose Circuit hosts the
 * navigable signed-in screens.
 */
@Composable
fun App(graph: AppGraph, modifier: Modifier = Modifier, onRootPop: () -> Unit = {}) {
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graph.authRepository.restore()
        restored = true
    }
    val session by graph.authRepository.session.collectAsState()
    setSingletonImageLoaderFactory { context -> ImageLoader.Builder(context).components { add(HeldPhotoFetcher.Factory()) }.build() }

    AppTheme(modifier) {
        if (!restored) return@AppTheme
        val current = session
        if (current == null) {
            CircuitCompositionLocals(graph.appCircuit.circuit) { CircuitContent(LoginScreen) }
        } else {
            key(current.did) {
                val sessionGraph = remember { graph.createSessionGraph(current) }
                // The image loader is app-wide; the photos it shows from this device's uploads are the signed-in session's.
                DisposableEffect(sessionGraph) {
                    val held = sessionGraph.feedRepository.heldPhotos
                    HeldPhotoFetcher.Factory.session.value = held
                    onDispose { HeldPhotoFetcher.Factory.session.compareAndSet(held, null) }
                }
                // Whether the athlete's PDS hosts spaces at all; until it answers, the session's scope decides.
                LaunchedEffect(Unit) { sessionGraph.sessionClients.resolveSpacesSupport() }
                LaunchedEffect(Unit) { sessionGraph.unitsRepository.refresh() }
                // Session-long, so a re-trim after a privacy zones save outlives the settings screen.
                LaunchedEffect(Unit) { sessionGraph.activityRepository.repairRoutesOnSave() }
                // Session-long too: leaderboard brackets refreshed at sign-in (a birthday) and after each profile save.
                LaunchedEffect(Unit) { sessionGraph.leaderboardBrackets.refreshOnSave() }
                // Once per account: legacy `recordingOf` groups become combined activities (retried each sign-in until all succeed).
                LaunchedEffect(Unit) { sessionGraph.legacyGroupMigration.runOncePerSession() }
                // So the AppView knows this athlete (and others can find them) before they post anything.
                LaunchedEffect(Unit) { sessionGraph.profileSetup.ensureProfile() }
                val units by sessionGraph.unitsRepository.units.collectAsState()
                CompositionLocalProvider(LocalUnitSystem provides units, LocalSubCircuit provides sessionGraph.subCircuit) {
                    CircuitCompositionLocals(sessionGraph.sessionCircuit.circuit) {
                        val backStack = rememberSaveableBackStack(root = FeedScreen)
                        val navigator = rememberCircuitNavigator(backStack = backStack, onRootPop = { onRootPop() })
                        AppShell(backStack, navigator, viewerDid = current.did.value) { contentModifier ->
                            NavigableCircuitContent(
                                navigator = navigator,
                                navStack = backStack,
                                modifier = contentModifier,
                                providedValues = rootFlags(backStack),
                                // iOS's edge swipe back and Android's predictive back; the default transition elsewhere.
                                decoratorFactory = remember { GestureNavigationDecorationFactory() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tells each screen whether it's the root of the stack ([LocalIsNavRoot]), which drops its back button. */
@Composable
private fun rootFlags(backStack: SaveableBackStack): Map<SaveableBackStack.Record, ProvidedValues> {
    val root = backStack.rootRecord
    return backStack.associateWith { record -> ProvidedValues { listOf(LocalIsNavRoot provides (record == root)) } }
}
