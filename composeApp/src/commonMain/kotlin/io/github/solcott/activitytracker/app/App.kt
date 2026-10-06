package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.ui.AppTheme
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem

/**
 * Root: signed out → [LoginScreen] on the app-scoped Circuit; signed in → a fresh `SessionGraph` (keyed by DID) whose Circuit hosts the
 * navigable signed-in screens.
 */
@Composable
fun App(graph: AppGraph, onRootPop: () -> Unit = {}) {
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graph.authRepository.restore()
        restored = true
    }
    val session by graph.authRepository.session.collectAsState()

    AppTheme {
        if (!restored) return@AppTheme
        val current = session
        if (current == null) {
            CircuitCompositionLocals(graph.appCircuit.circuit) { CircuitContent(LoginScreen) }
        } else {
            key(current.did) {
                val sessionGraph = remember { graph.createSessionGraph(current) }
                LaunchedEffect(Unit) { sessionGraph.unitsRepository.refresh() }
                // So the AppView knows this athlete (and others can find them) before they post anything.
                LaunchedEffect(Unit) { sessionGraph.profileSetup.ensureProfile() }
                val units by sessionGraph.unitsRepository.units.collectAsState()
                CompositionLocalProvider(LocalUnitSystem provides units) {
                    CircuitCompositionLocals(sessionGraph.sessionCircuit.circuit) {
                        val backStack = rememberSaveableBackStack(root = FeedScreen)
                        val navigator = rememberCircuitNavigator(backStack = backStack, onRootPop = { onRootPop() })
                        NavigableCircuitContent(navigator = navigator, backStack = backStack)
                    }
                }
            }
        }
    }
}
