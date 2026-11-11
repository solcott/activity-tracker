package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Sub-screens run by the host (dev/presenter-host, "The host runs a screen's sub-screens" and "A sub-screen keeps its state"). */
@OptIn(ExperimentalCoroutinesApi::class)
class HostSubRecordTest {
    private val fakes = FakePresenters()
    private val subFakes = FakeSubPresenters()
    private val host =
        SessionHost(
            session = sessionOf("did:plc:alice"),
            factories = fakes.factories,
            subFactories = subFakes.factories,
            runner = SessionRunner(OpenSteps()),
            recording = { false },
            recordRequests = RecordScreenRequests(),
            accountRequests = AccountScreenRequests(),
        )
    private val feed = host.stack(TopLevelDestination.Feed)
    private val filter = TestSubScreen("filter")
    private val outer = mutableListOf<TestOuterEvent>()

    private fun TestScope.start() {
        backgroundScope.launch { host.run(HostClock.Immediate) }
        runCurrent()
    }

    private val top: HostRecord
        get() = feed.records.value.last()

    private fun HostRecord.filterBar(screen: TestSubScreen = filter): HostSubRecord = sub(screen) { outer += it as TestOuterEvent }

    @Test
    fun aSubScreenPublishesStatesTakesEventsAndReportsOuterEvents() = runTest {
        start()
        val bar = top.filterBar()
        assertNull(bar.state.value)
        runCurrent()

        assertEquals(0, bar.test.value)
        assertEquals(listOf<TestOuterEvent>(TestOuterEvent.Changed(0)), outer)

        bar.test.eventSink(TestSubEvent.Set(30))
        runCurrent()

        assertEquals(30, bar.test.value)
        assertEquals(TestOuterEvent.Changed(30), outer.last())
        assertEquals("TestSubScreen", bar.screenName)
    }

    @Test
    fun askingAgainGivesTheSameSubScreenSendingToTheNewestSink() = runTest {
        start()
        val bar = top.filterBar()
        runCurrent()
        bar.test.eventSink(TestSubEvent.Set(7))
        runCurrent()
        val newer = mutableListOf<TestOuterEvent>()

        val again = top.sub(TestSubScreen("filter")) { newer += it as TestOuterEvent }
        again.test.eventSink(TestSubEvent.Set(8))
        runCurrent()

        assertSame(bar, again)
        assertEquals(8, again.test.value)
        assertEquals(listOf<TestOuterEvent>(TestOuterEvent.Changed(8)), newer)
    }

    @Test
    fun aScreenThatIsNotEqualReplacesTheOneHeldAndStartsAfresh() = runTest {
        start()
        val bar = top.filterBar()
        runCurrent()
        bar.test.eventSink(TestSubEvent.Set(7))
        runCurrent()

        val replaced = top.filterBar(TestSubScreen("filter", initial = 1))
        runCurrent()

        assertNotSame(bar, replaced)
        assertEquals(1, replaced.test.value)
        assertNotEquals(bar.test.retained, replaced.test.retained)
        assertEquals(listOf(TestSubScreen("filter", initial = 1)), subFakes.running)
    }

    @Test
    fun aCoveredScreensSubScreenStopsAndComesBackWithItsState() = runTest {
        start()
        val feedRecord = top
        val bar = feedRecord.filterBar()
        runCurrent()
        bar.test.eventSink(TestSubEvent.Set(7))
        runCurrent()
        val first = bar.test

        feed.goTo(TestScreen("activity"))
        runCurrent()

        assertEquals(emptyList(), subFakes.running)
        assertEquals(7, bar.test.value)

        feed.pop()
        runCurrent()

        assertEquals(listOf(filter), subFakes.running)
        assertEquals(7, bar.test.value)
        assertEquals(first.retained, bar.test.retained)
        assertEquals(first.saved, bar.test.saved)
    }

    @Test
    fun anotherTabStopsItAndChoosingItBringsItBack() = runTest {
        start()
        val bar = top.filterBar()
        runCurrent()
        bar.test.eventSink(TestSubEvent.Set(5))
        runCurrent()

        host.select(TopLevelDestination.Search)
        runCurrent()
        assertEquals(emptyList(), subFakes.running)

        host.select(TopLevelDestination.Feed)
        runCurrent()
        assertEquals(5, bar.test.value)
        assertEquals(listOf(filter), subFakes.running)
    }

    @Test
    fun aSubScreenOfAScreenThatLeftItsStackIsForgotten() = runTest {
        start()
        val page = TestScreen("page")
        feed.goTo(page)
        runCurrent()
        val bar = top.filterBar()
        runCurrent()
        bar.test.eventSink(TestSubEvent.Set(7))
        runCurrent()
        val first = bar.test

        feed.pop()
        runCurrent()
        feed.goTo(page)
        runCurrent()
        val reopened = top.filterBar()
        runCurrent()

        assertEquals(0, reopened.test.value)
        assertNotEquals(first.retained, reopened.test.retained)
        assertNotEquals(first.saved, reopened.test.saved)
    }

    @Test
    fun aClosedSubScreenStopsAndAnEqualOneStartsAfresh() = runTest {
        start()
        val sheet = top.filterBar()
        runCurrent()
        sheet.test.eventSink(TestSubEvent.Set(7))
        runCurrent()
        val first = sheet.test

        sheet.close()
        runCurrent()
        assertEquals(emptyList(), subFakes.running)
        sheet.close()

        val reopened = top.filterBar()
        runCurrent()

        assertNotSame(sheet, reopened)
        assertEquals(0, reopened.test.value)
        assertNotEquals(first.retained, reopened.test.retained)
    }

    @Test
    fun subScreensOfDifferentClassesRunSideBySide() = runTest {
        start()
        val bar = top.filterBar()
        val other = top.sub(OtherTestSubScreen("sheet")) { outer += it as TestOuterEvent }
        runCurrent()

        assertEquals(setOf(filter, TestSubScreen("sheet")), subFakes.running.toSet())
        assertEquals(0, bar.test.value)
        assertEquals(TestSubScreen("sheet"), other.test.screen)
    }

    @Test
    fun askingForWhatIsNotASubScreenOrHasNoPresenterFailsNamingIt() = runTest {
        start()

        val notSub = assertFailsWith<IllegalArgumentException> { top.sub(TestScreen("page")) {} }
        assertContains(notSub.message.orEmpty(), "page")

        val unknown = assertFailsWith<IllegalStateException> { top.sub(UnknownSubScreen) {} }
        assertContains(unknown.message.orEmpty(), "UnknownSubScreen")
    }
}

/** A sub-screen no fake presents. */
private data object UnknownSubScreen : com.slack.circuit.subcircuit.SubScreen<TestOuterEvent>
