package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope

/** The one flight for asking and ending the session, so a second tap can neither ask twice nor end it twice. */
private object EndSession

@CircuitInject(AccountScreen::class, SessionScope::class)
@Composable
fun AccountPresenter(navigator: Navigator, clients: SessionClients, auth: AuthRepository, check: SignOutCheck): AccountState {
    // The PDS's answer about Spaces can land after the page opens, and changes what the grant means.
    val support by clients.spacesSupport.collectAsState()
    val grant = remember(support) { clients.grantStatus }
    var confirming by remember { mutableStateOf<EndingSession?>(null) }
    val flights = rememberSingleFlight()

    // Dropping the session tears down the SessionGraph and returns to LoginScreen, so nothing after the end runs here.
    suspend fun end(signInAgain: Boolean) {
        if (signInAgain) auth.signInAgain() else auth.signOut()
    }

    fun ask(signInAgain: Boolean) {
        flights.launch(EndSession) {
            // A check that can't read the device's queue ends the session as asked: the queued writes stay on the device either way.
            val pending = runSuspendCatching { check.pending() }.getOrNull()
            if (pending == null) end(signInAgain) else confirming = EndingSession(signInAgain, pending)
        }
    }

    return AccountState(
        handle = clients.handle.value,
        did = clients.did.value,
        pdsHost = hostOf(clients.pdsUrl),
        grant = grant,
        confirming = confirming,
        ending = flights.isRunning(EndSession),
    ) { event ->
        when (event) {
            AccountEvent.Back -> navigator.pop()
            AccountEvent.SignOut -> if (confirming == null) ask(signInAgain = false)
            AccountEvent.SignInAgain -> if (confirming == null) ask(signInAgain = true)
            is AccountEvent.Confirm -> {
                confirming = null
                flights.launch(EndSession) { end(event.signInAgain) }
            }
            AccountEvent.Dismiss -> confirming = null
        }
    }
}

/** The host of [url], `pds.example.com` for `https://pds.example.com:443/`: what the athlete knows their server by. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { url }
