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
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.nav.AppShell
import io.github.solcott.activitytracker.app.nav.appNavDecoration
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.ui.HeldPhotoFetcher
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsNavRoot
import io.github.solcott.activitytracker.app.ui.onRouteImageSession
import io.github.solcott.activitytracker.app.ui.theme.AppTheme
import io.github.solcott.activitytracker.atproto.runSuspendCatching

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
                // Session-long: queued writes go on their own, waiting out the PDS's rate limits (data/outbox).
                LaunchedEffect(Unit) { sessionGraph.outboxPacer.run() }
                // Finished uploads a week old leave the queue; closing mid-import asks first on desktop and web (data/upload-queue).
                LaunchedEffect(Unit) { runSuspendCatching { sessionGraph.uploadQueue.prune() } }
                // A batch save left unfinished (the app was killed, or quit) resumes, and what no save names any more is let go
                // (feed/batch-import).
                LaunchedEffect(Unit) {
                    runSuspendCatching {
                        graph.importSaves.resume(current.did, sessionGraph.importSaveStore, sessionGraph.importSaveRunner)
                    }
                }
                // A data export left running (the app was killed, or quit) goes on; one a week past ready is deleted (auth/data-export).
                LaunchedEffect(Unit) {
                    runSuspendCatching { graph.dataExports.resume(current.did) { sessionGraph.dataExporter.runCurrent() } }
                }
                val importsActive by rememberUpdatedState(onImportsActive)
                LaunchedEffect(Unit) { sessionGraph.uploadQueue.importsActive.collect { importsActive(it) } }
                DisposableEffect(Unit) { onDispose { importsActive(false) } }
                // Once per account: legacy `recordingOf` groups become combined activities (retried each sign-in until all succeed).
                LaunchedEffect(Unit) { sessionGraph.legacyGroupMigration.runOncePerSession() }
                // So the AppView knows this athlete (and others can find them) before they post anything.
                LaunchedEffect(Unit) { sessionGraph.profileSetup.ensureProfile() }
                val units by sessionGraph.unitsRepository.units.collectAsState()
                CompositionLocalProvider(LocalUnitSystem provides units, LocalSubCircuit provides sessionGraph.subCircuit) {
                    val circuit = sessionGraph.sessionCircuit.circuit
                    CircuitCompositionLocals(circuit) {
                        val backStack = rememberSaveableBackStack(root = FeedScreen)
                        val navigator = rememberCircuitNavigator(backStack = backStack, onRootPop = { onRootPop() })
                        // An unfinished recording of this account's, from before the app stopped, is offered back first, and one
                        // still going since its session expired is shown as it is (record/gps-recording).
                        LaunchedEffect(Unit) {
                            graph.activityRecorder.recover(current.did.value)
                            // After the first frame: navigating while the root screen is still being placed crashes Compose's insets on
                            // iOS.
                            withFrameNanos {}
                            val recording = graph.activityRecorder.state.value
                            if (recording.recovered || recording.inProgressFor(current.did.value)) navigator.goTo(RecordScreen)
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
                        AppShell(backStack, navigator, viewerDid = current.did.value) { contentModifier ->
                            NavigableCircuitContent(
                                navigator = navigator,
                                navStack = backStack,
                                modifier = contentModifier,
                                providedValues = rootFlags(backStack),
                                // Settings beside its page on a wide window; otherwise iOS's edge swipe back and Android's
                                // predictive back, and the default transition elsewhere.
                                decoration = remember(circuit) { appNavDecoration(circuit) },
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
