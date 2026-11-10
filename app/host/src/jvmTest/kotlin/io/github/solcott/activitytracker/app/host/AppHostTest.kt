package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Which of sign-in and a signed-in session the app shows (dev/presenter-host, ui/ios-native-shell "The app follows the session"). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppHostTest {
    private val fakes = FakePresenters()
    private val auth = FakeAuth()
    private val host = AppHost(auth, fakes.factories)
    private val made = mutableListOf<SessionHost>()
    private val sessions = SessionHostFactory { session ->
        SessionHost(session, fakes.factories, SessionRunner(NoLeaveSteps()), { false }, RecordScreenRequests(), AccountScreenRequests())
            .also { made += it }
    }

    private fun TestScope.start() {
        backgroundScope.launch { host.run(HostClock.Immediate, sessions) }
        runCurrent()
    }

    @Test
    fun nothingShowsUntilTheStoredSessionIsRead() = runTest {
        auth.restoring = CompletableDeferred()
        start()

        assertEquals(RootState.Restoring, host.root.value)
        assertEquals(emptyList(), fakes.running)

        auth.restoring?.complete(Unit)
        runCurrent()
        assertIs<RootState.SignedOut>(host.root.value)
    }

    @Test
    fun signedOutShowsTheSignInScreenRunning() = runTest {
        start()

        val root = assertIs<RootState.SignedOut>(host.root.value)
        assertEquals(LoginScreen, root.login.screen)
        assertEquals(LoginScreen, root.login.test.screen)
        assertEquals(listOf<Any>(LoginScreen), fakes.running)
    }

    @Test
    fun aStoredSessionOpensSignedIn() = runTest {
        auth.stored = sessionOf("did:plc:alice")
        start()

        val root = assertIs<RootState.SignedIn>(host.root.value)
        assertEquals("did:plc:alice", root.session.did.value)
    }

    @Test
    fun signingInShowsTheSessionStartingOnTheFeed() = runTest {
        start()

        auth.session.value = sessionOf("did:plc:alice")
        runCurrent()

        val root = assertIs<RootState.SignedIn>(host.root.value)
        assertEquals(TopLevelDestination.Feed, root.session.selected.value)
        assertEquals(listOf(root.session.stack(TopLevelDestination.Feed).records.value.single().screen), fakes.running)
    }

    @Test
    fun aRefreshedTokenKeepsTheSession() = runTest {
        auth.stored = sessionOf("did:plc:alice")
        start()
        val before = assertIs<RootState.SignedIn>(host.root.value).session

        auth.session.value = sessionOf("did:plc:alice", accessToken = "refreshed")
        runCurrent()

        assertSame(before, assertIs<RootState.SignedIn>(host.root.value).session)
        assertEquals(1, made.size)
    }

    @Test
    fun signingOutAndInAsAnotherAccountStartsANewSession() = runTest {
        auth.stored = sessionOf("did:plc:alice")
        start()
        val alice = assertIs<RootState.SignedIn>(host.root.value).session
        alice.stack(TopLevelDestination.Feed).goTo(TestScreen("activity"))
        runCurrent()

        auth.session.value = null
        runCurrent()
        assertIs<RootState.SignedOut>(host.root.value)
        assertEquals(listOf<Any>(LoginScreen), fakes.running, "the old session's presenters stopped")

        auth.session.value = sessionOf("did:plc:bob")
        runCurrent()
        val bob = assertIs<RootState.SignedIn>(host.root.value).session
        assertNotSame(alice, bob)
        assertEquals("did:plc:bob", bob.did.value)
        assertEquals(1, bob.stack(TopLevelDestination.Feed).records.value.size)
    }

    @Test
    fun signingInAgainToTheSameAccountStartsAfresh() = runTest {
        auth.stored = sessionOf("did:plc:alice")
        start()
        val first = assertIs<RootState.SignedIn>(host.root.value).session
        first.stack(TopLevelDestination.Feed).goTo(TestScreen("activity"))

        auth.session.value = null
        runCurrent()
        auth.session.value = sessionOf("did:plc:alice")
        runCurrent()

        val second = assertIs<RootState.SignedIn>(host.root.value).session
        assertNotSame(first, second)
        assertEquals(1, second.stack(TopLevelDestination.Feed).records.value.size)
    }

    @Test
    fun eachSignOutShowsAFreshSignInScreen() = runTest {
        start()
        val first = assertIs<RootState.SignedOut>(host.root.value).login

        auth.session.value = sessionOf("did:plc:alice")
        runCurrent()
        // An expired session ends the way a sign-out does.
        auth.session.value = null
        runCurrent()

        val second = assertIs<RootState.SignedOut>(host.root.value).login
        assertNotSame(first, second)
        assertNotSame(first.key, second.key)
    }
}

private class FakeAuth : HostAuth {
    override val session = MutableStateFlow<OAuthSession?>(null)
    var stored: OAuthSession? = null
    var restoring: CompletableDeferred<Unit>? = null

    override suspend fun restore() {
        restoring?.await()
        session.value = stored
    }

    override suspend fun endExpiredSessions() {
        awaitCancellation()
    }
}

private class NoLeaveSteps : SessionSteps {
    override val leaving: Flow<Boolean> = flowOf(false)
    override val left = MutableStateFlow(false)

    override suspend fun resolveSpacesSupport() = Unit

    override suspend fun refreshUnits() = Unit

    override suspend fun checkStillHere() = Unit

    override suspend fun runWork() {
        awaitCancellation()
    }
}
