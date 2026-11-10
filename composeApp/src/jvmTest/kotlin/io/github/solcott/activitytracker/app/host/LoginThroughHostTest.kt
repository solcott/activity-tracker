package io.github.solcott.activitytracker.app.host

import co.touchlab.kermit.Logger
import com.slack.circuit.runtime.presenter.Presenter
import io.github.solcott.activitytracker.app.login.LoginEvent
import io.github.solcott.activitytracker.app.login.LoginPresenter
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.login.LoginState
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.auth.AuthBrowser
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.export.testDataExports
import io.github.solcott.activitytracker.data.importsave.testImportSaves
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * The real sign-in presenter, over a real auth repository, run by the presenter host with no UI (dev/presenter-host): what the iOS shell
 * does. The host's own tests use fake presenters; this holds that a real one, with its retained state, its text field and its coroutines,
 * works the same way there as under Circuit.
 */
class LoginThroughHostTest {
    private val resolved = mutableListOf<String?>()

    /** Held shut so the sign-in stays at "starting" until the test has seen it. */
    private val answer = CompletableDeferred<Unit>()
    private val http =
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/com.atproto.identity.resolveHandle")) {
                    resolved += request.url.parameters["handle"]
                    answer.await()
                }
                respond("", HttpStatusCode.NotFound)
            },
        ) {
            expectSuccess = false
        }
    private val db = inMemoryDatabase()
    private val recorder = ActivityRecorder(FakeLocationTracker(), Clock.System)
    private val config =
        AtprotoConfig(
            plcUrl = "http://localhost:2582",
            handleResolverUrl = "http://localhost:2583",
            appViewUrl = "http://localhost:8081",
            appViewDid = "did:web:localhost%3A8081",
            oauth = OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
        )
    private val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
    private val manager = OAuthSessionManager(OAuthClient(http, IdentityResolver(http, config), config.oauth), store)
    private val auth = AuthRepository(manager, db, http, recorder, testImportSaves(db), testDataExports(db))
    private val presenter = LoginPresenter(AuthCoordinator(auth, AuthBrowser {}, Logger), auth, recorder)

    /** As the app graph's generated factory answers: the sign-in presenter for the sign-in screen, nothing for any other. */
    private val factories = setOf(Presenter.Factory { screen, _, _ -> presenter.takeIf { screen == LoginScreen } })
    private val host = appHostOf(RepositoryHostAuth(auth), factories)

    @Test
    fun aHandleTypedFromOutsideAndSubmitStartsSignIn() = runTest {
        backgroundScope.launch { host.run(HostClock.Immediate) { error("nobody signs in here") } }
        val login = host.root.filterIsInstance<RootState.SignedOut>().first().login
        val first = login.state.filterIsInstance<LoginState>().first()
        assertIs<Status.Idle>(first.status)

        // What a native field does: sets the text on the state's own field, then sends the event.
        first.handle.setTextAndPlaceCursorAtEnd("Alice.Test")
        first.eventSink(LoginEvent.Submit)

        val starting = login.state.filterIsInstance<LoginState>().first { it.status is Status.Starting }
        assertEquals(true, starting.busy)
        answer.complete(Unit)
        val failed = login.state.filterIsInstance<LoginState>().first { it.status is Status.Failed }
        assertEquals(Status.Stage.FindAccount, assertIs<Status.Failed>(failed.status).stage)
        assertEquals(listOf<String?>("alice.test"), resolved, "the presenter read the text set from outside the composition")
    }
}
