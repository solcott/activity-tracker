package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Stable
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.auth.AuthRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** What the app shows at its root. */
sealed interface RootState {
    /** Not yet known whether an account is signed in: nothing shows, for the moment the stored session takes to read. */
    data object Restoring : RootState

    /** No account is signed in: the sign-in screen, [login]. */
    data class SignedOut(val login: HostRecord) : RootState

    /** An account is signed in: its [session]. */
    data class SignedIn(val session: SessionHost) : RootState
}

/**
 * The app's screens, run with no UI (dev/presenter-host): which of sign-in and a signed-in session shows, and their presenters. A native UI
 * follows [root] and draws the records it leads to. The Compose shell does the same work itself, in `App`.
 *
 * Nothing runs until [run] is called. Main thread only.
 */
@Stable
@SingleIn(AppScope::class)
@Inject
class AppHost
// Internal, so the export to Swift doesn't follow it to Circuit's presenter types, one of which it can't carry (CircuitContext's
// map of tags); Swift is handed a host and never makes one. Other modules' tests use [appHostOf].
internal constructor(private val auth: HostAuth, factories: Set<Presenter.Factory>) {
    private val presenters = Presenters(factories)
    private val keys = RecordKeys()

    val root: StateFlow<RootState>
        field = MutableStateFlow<RootState>(RootState.Restoring)

    /**
     * Runs the app's screens until cancelled. Restores the stored session, then shows sign-in while no account is signed in and a
     * [SessionHost] from [sessions] while one is: a new one for each account, and for each sign-in.
     */
    suspend fun run(clock: HostClock, sessions: SessionHostFactory): Unit = coroutineScope {
        // A session whose refresh was refused ends the way a sign-out does, for as long as the app runs (auth/oauth-session).
        launch { auth.endExpiredSessions() }
        auth.restore()
        // By account, not by session: a refreshed token is a new session object for the same account, which keeps its host.
        auth.session
            .distinctUntilChangedBy { it?.did }
            .collectLatest { session ->
                if (session == null) {
                    // A new record each time, so the sign-in screen starts afresh after a sign-out.
                    val login = HostRecord(keys.next(), LoginScreen, isRoot = true, showsTabs = false, presenter = loginPresenter())
                    root.value = RootState.SignedOut(login)
                    compose(clock) { RunRecord(login) }
                } else {
                    val host = sessions.create(session)
                    root.value = RootState.SignedIn(host)
                    host.run(clock)
                }
            }
    }

    private fun loginPresenter(): Presenter<*> = presenters.create(LoginScreen, Navigator.NoOp)
}

/** An [AppHost] over [factories], for a test outside this module that runs real presenters through it; the app's comes from its graph. */
fun appHostOf(auth: HostAuth, factories: Set<Presenter.Factory>): AppHost = AppHost(auth, factories)

/** Makes the [SessionHost] of a signed-in account. The app implements it with the account's session graph. */
fun interface SessionHostFactory {
    fun create(session: OAuthSession): SessionHost
}

/** The signed-in account, as [AppHost] follows it. An interface so the host is tested without a stored session. */
interface HostAuth {
    /** The signed-in account's session, or null when none is signed in. */
    val session: StateFlow<OAuthSession?>

    /** Reads the stored session, if any, into [session]. */
    suspend fun restore()

    /** Ends each session whose refresh the server refuses, until cancelled. */
    suspend fun endExpiredSessions()
}

/** [HostAuth] over the app's auth repository. */
@ContributesBinding(AppScope::class)
@Inject
class RepositoryHostAuth(private val auth: AuthRepository) : HostAuth {
    override val session: StateFlow<OAuthSession?>
        get() = auth.session

    override suspend fun restore() {
        auth.restore()
    }

    override suspend fun endExpiredSessions() = auth.endExpiredSessions()
}
