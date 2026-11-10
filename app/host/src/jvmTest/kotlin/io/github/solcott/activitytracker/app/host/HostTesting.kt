package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.rememberTextFieldState

/** A screen of the tests' own, beside the app's real ones, which the fake presenters take too. */
data class TestScreen(val name: String) : Screen

/** What a [FakePresenters] presenter publishes: enough to see whether it ran, what it kept, and what it was told. */
data class TestState(
    val screen: Screen,
    /** Given out when the presenter first retained it: the same for as long as that state is kept. */
    val retained: Int,
    /** The same, for saved state. */
    val saved: Int,
    val bumps: Int,
    val field: TextFieldState,
    /** [field]'s text as the presenter read it. */
    val text: String,
    val eventSink: (TestEvent) -> Unit,
) : CircuitUiState

sealed interface TestEvent {
    data object Bump : TestEvent

    data class GoTo(val screen: Screen) : TestEvent

    data object Pop : TestEvent
}

/** Presenters for any screen, which record which of them are running. */
class FakePresenters {
    /** The screens whose presenters are in the composition now. */
    val running = mutableListOf<Screen>()

    /** Screens no presenter is registered for. */
    val unknown = mutableSetOf<Screen>()
    private var tokens = 0

    val factories: Set<Presenter.Factory> =
        setOf(Presenter.Factory { screen, navigator, _ -> if (screen in unknown) null else FakePresenter(screen, navigator) })

    private inner class FakePresenter(private val screen: Screen, private val navigator: Navigator) : Presenter<TestState> {
        @Composable
        override fun present(): TestState {
            val retained = rememberRetained { ++tokens }
            val saved = rememberSaveable { ++tokens }
            var bumps by remember { mutableIntStateOf(0) }
            val field = rememberTextFieldState()
            DisposableEffect(Unit) {
                running += screen
                onDispose { running -= screen }
            }
            return TestState(screen, retained, saved, bumps, field, field.text.toString()) { event ->
                when (event) {
                    TestEvent.Bump -> bumps++
                    is TestEvent.GoTo -> navigator.goTo(event.screen)
                    TestEvent.Pop -> navigator.pop()
                }
            }
        }
    }
}

/** The state a record published, as the fake presenters' type. */
val HostRecord.test: TestState
    get() = checkNotNull(state.value) { "$screen has published no state" } as TestState

fun sessionOf(did: String, accessToken: String = "token"): OAuthSession =
    OAuthSession(
        did = Did(did),
        handle = Handle("alice.test"),
        pdsUrl = "https://pds.test",
        issuer = "https://pds.test",
        tokenEndpoint = "https://pds.test/oauth/token",
        accessToken = accessToken,
        refreshToken = null,
        expiresAt = null,
        scope = "atproto",
        dpopKey = DpopKey.Serialized(privateKeyDer = "", publicKeyRaw = ""),
    )
