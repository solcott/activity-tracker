package io.github.solcott.activitytracker.app.host

import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.popUntil
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreen
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** A tab's stack and the navigator presenters drive (dev/presenter-host, "Presenters navigate the host's stacks"). */
@OptIn(ExperimentalCoroutinesApi::class)
class TabStackTest {
    private val fakes = FakePresenters()
    private val presenters = Presenters(fakes.factories)
    private val keys = RecordKeys()
    private val feed = TestScreen("feed")
    private val activity = TestScreen("activity")
    private val edit = TestScreen("edit")
    private val stack = TabStack(TopLevelDestination.Feed, feed, presenters, keys)

    private val screens
        get() = stack.records.value.map { it.screen }

    @Test
    fun startsAtItsRoot() {
        assertEquals(listOf(feed), screens)
        assertTrue(stack.records.value.single().isRoot)
        assertEquals(feed, stack.peek())
    }

    @Test
    fun goingToAScreenPutsItOnTopOfThisStackOnly() {
        val search = TabStack(TopLevelDestination.Search, TestScreen("search"), presenters, keys)

        assertTrue(stack.goTo(activity))

        assertEquals(listOf(feed, activity), screens)
        assertFalse(stack.records.value.last().isRoot)
        assertEquals(1, search.records.value.size)
    }

    @Test
    fun poppingRemovesTheTopScreenAndReturnsIt() {
        stack.goTo(activity)

        assertEquals(activity, stack.pop())

        assertEquals(listOf(feed), screens)
    }

    @Test
    fun theFirstScreenCannotBePopped() {
        assertNull(stack.pop())
        assertEquals(listOf(feed), screens)
    }

    @Test
    fun goingToTheScreenOnTopDoesNothing() {
        stack.goTo(activity)
        val before = stack.records.value

        assertFalse(stack.goTo(activity))

        assertEquals(before, stack.records.value)
    }

    @Test
    fun theSameScreenTwiceInAStackIsTwoRecords() {
        stack.goTo(activity)
        stack.goTo(edit)
        stack.goTo(activity)

        assertEquals(listOf(feed, activity, edit, activity), screens)
        assertEquals(4, stack.records.value.map { it.key }.toSet().size)
    }

    @Test
    fun theUiClosesSeveralScreensAtOnce() {
        stack.goTo(activity)
        stack.goTo(edit)

        stack.popTo(1)

        assertEquals(listOf(feed), screens)
    }

    @Test
    fun popToNeverEmptiesOrLengthensTheStack() {
        stack.goTo(activity)
        val before = stack.records.value

        stack.popTo(5)
        assertEquals(before, stack.records.value)
        stack.popTo(0)
        assertEquals(listOf(feed), screens)
    }

    @Test
    fun popUntilStopsAtTheScreenAsked() {
        stack.goTo(activity)
        stack.goTo(edit)

        stack.popUntil { it == activity }
        assertEquals(listOf(feed, activity), screens)

        stack.popUntil { it == TestScreen("nowhere") }
        assertEquals(listOf(feed), screens, "a screen not in the stack stops at the root")
    }

    @Test
    fun peekBackStackIsTopFirstAsCircuitsIs() {
        stack.goTo(activity)

        assertEquals(listOf(activity, feed), stack.peekBackStack())
        assertNull(stack.peekNavStack())
    }

    @Test
    fun resetRootReplacesTheStack() {
        stack.goTo(activity)
        val other = TestScreen("other")

        assertEquals(listOf(activity, feed), stack.resetRoot(other, Navigator.StateOptions.SaveAndRestore))

        assertEquals(listOf(other), screens)
        assertTrue(stack.records.value.single().isRoot)
    }

    @Test
    fun thereIsNoForwardHistory() {
        stack.goTo(activity)
        stack.pop()

        assertFalse(stack.forward())
        assertFalse(stack.backward())
        assertEquals(listOf(feed), screens)
    }

    @Test
    fun aRecordNamesItsScreensClassForSwift() {
        stack.goTo(RecordScreen)

        assertEquals("TestScreen", stack.records.value.first().screenName)
        assertEquals("RecordScreen", stack.records.value.last().screenName)
    }

    @Test
    fun aWholeWindowFlowHidesTheTabs() {
        stack.goTo(RecordScreen)

        assertTrue(stack.records.value.first().showsTabs)
        assertFalse(stack.records.value.last().showsTabs)
    }

    @Test
    fun whatSwiftFollowsIsAlwaysTheStackItself() = runTest {
        val seen = mutableListOf<List<Screen>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            stack.shown.collect { shown -> seen += shown.records.map { it.screen } }
        }

        stack.goTo(activity)
        stack.pop()

        assertEquals(listOf<List<Screen>>(listOf(feed), listOf(feed, activity), listOf(feed)), seen)
        assertEquals(stack.records.value, stack.shown.value.records)
    }

    @Test
    fun aScreenWithNoPresenterFailsNamingIt() {
        val orphan = TestScreen("orphan")
        fakes.unknown += orphan

        val failure = assertFailsWith<IllegalStateException> { stack.goTo(orphan) }

        assertContains(failure.message.orEmpty(), "TestScreen(name=orphan)")
        assertEquals(listOf(feed), screens)
    }
}
