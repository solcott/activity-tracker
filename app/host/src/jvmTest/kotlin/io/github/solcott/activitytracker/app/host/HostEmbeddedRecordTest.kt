package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Screens a record embeds (dev/presenter-host, "A screen can embed another screen"). */
@OptIn(ExperimentalCoroutinesApi::class)
class HostEmbeddedRecordTest {
    private val fakes = FakePresenters()
    private val host =
        SessionHost(
            session = sessionOf("did:plc:alice"),
            factories = fakes.factories,
            subFactories = FakeSubPresenters().factories,
            runner = SessionRunner(OpenSteps()),
            recording = { false },
            recordRequests = RecordScreenRequests(),
            accountRequests = AccountScreenRequests(),
        )
    private val search = host.stack(TopLevelDestination.Search)
    private val browse = TestScreen("browse")

    private fun TestScope.start() {
        backgroundScope.launch { host.run(HostClock.Immediate) }
        host.select(TopLevelDestination.Search)
        runCurrent()
    }

    private val root: HostRecord
        get() = search.records.value.first()

    @Test
    fun anEmbeddedScreenRunsInsideItsRecordAndNavigatesItsStack() = runTest {
        start()
        val inner = root.embed(browse)
        assertNull(inner.state.value)
        runCurrent()

        assertEquals(browse, inner.test.screen)
        assertTrue(browse in fakes.running)
        inner.test.eventSink(TestEvent.GoTo(TestScreen("segment")))
        runCurrent()

        assertEquals(TestScreen("segment"), search.records.value.last().screen)
        assertEquals(2, search.records.value.size)
        // Covered, the root stops, and the screen it embeds with it.
        assertTrue(browse !in fakes.running)
    }

    @Test
    fun askingAgainGivesTheSameRecordWithItsState() = runTest {
        start()
        val inner = root.embed(browse)
        runCurrent()
        inner.test.eventSink(TestEvent.Bump)
        runCurrent()

        val again = root.embed(TestScreen("browse"))
        assertSame(inner, again)
        assertEquals(1, again.test.bumps)
        assertNotEquals(root.key, inner.key)
    }

    @Test
    fun itKeepsItsStateWhileItsRecordIsCovered() = runTest {
        start()
        val inner = root.embed(browse)
        runCurrent()
        val retained = inner.test.retained
        inner.test.eventSink(TestEvent.GoTo(TestScreen("segment")))
        runCurrent()
        search.records.value.last().test.eventSink(TestEvent.Pop)
        runCurrent()

        assertTrue(browse in fakes.running)
        assertEquals(retained, inner.test.retained)
    }

    @Test
    fun onlyAScreenCanBeEmbedded() = runTest {
        start()
        assertFailsWith<IllegalArgumentException> { root.embed(TestSubScreen("filter")) }
    }
}
