package io.github.solcott.activitytracker.app.host

import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.popUntil
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SettingsPane
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** A tab's stack and the navigator presenters drive (dev/presenter-host, "Presenters navigate the host's stacks"). */
@OptIn(ExperimentalCoroutinesApi::class)
/** A height with room for two panes, for tests that are about the width. */
internal const val TALL = 1_000.0

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

    // Settings beside its page (dev/presenter-host, "The host says when Settings can show beside its page").

    private val profile = ProfileScreen("did:plc:alice")
    private val blocked = BlockListScreen(BlockListKind.Blocked)

    private fun open(vararg screens: Screen) = screens.forEach { stack.goTo(it) }

    private val split
        get() = stack.shown.value.split

    private val pushed
        get() = stack.shown.value.pushed.map { it.screen }

    @Test
    fun aNarrowUiShowsOneScreenAtATime() {
        open(SettingsScreen, PrivacyZonesScreen)

        assertNull(split, "no width reported yet")
        stack.setSize(699.0, TALL)
        assertNull(split)
        assertEquals(screens, pushed)
    }

    @Test
    fun aPhoneOnItsSideShowsOneScreenAtATime() {
        stack.setSize(956.0, 440.0)
        open(SettingsScreen, PrivacyZonesScreen)

        assertNull(split, "wide enough, but too short for two panes")
        assertEquals(screens, pushed)
    }

    @Test
    fun turningATabletKeepsSettingsBesideItsPage() {
        stack.setSize(834.0, 1_210.0)
        open(SettingsScreen, PrivacyZonesScreen)
        val upright = assertNotNull(split)

        stack.setSize(1_210.0, 834.0)
        assertEquals(upright.page, split?.page)
    }

    @Test
    fun aWideUiShowsSettingsBesideItsPage() {
        stack.setSize(700.0, TALL)
        open(SettingsScreen, PrivacyZonesScreen)

        val split = assertNotNull(split)
        assertEquals(SettingsScreen, split.settings.screen)
        assertEquals(PrivacyZonesScreen, split.page.screen)
        assertEquals(SettingsPane(twoPane = true, shownPage = PrivacyZonesScreen), split.pane)
        assertEquals(listOf(feed, SettingsScreen), pushed, "the page is drawn beside Settings, not pushed over it")
    }

    @Test
    fun aDeeperPageShowsBesideTheListWithTheFirstPagesRowMarked() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, TrainingZonesScreen, SportZonesScreen("run"))

        val split = assertNotNull(split)
        assertEquals(SportZonesScreen("run"), split.page.screen)
        assertEquals(TrainingZonesScreen, split.pane.shownPage)
        assertEquals(listOf(feed, SettingsScreen), pushed)
    }

    @Test
    fun choosingAnotherRowReplacesThePage() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, PrivacyZonesScreen)

        // What Settings' presenter does for a row.
        stack.popUntil { it == SettingsScreen }
        stack.goTo(blocked)

        assertEquals(blocked, split?.page?.screen)
        assertEquals(blocked, split?.pane?.shownPage)
    }

    @Test
    fun anyOtherScreenOnTopShowsFullWidthAndBackRestoresTheSplit() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, blocked, profile)

        assertNull(split)
        assertEquals(listOf(feed, SettingsScreen, blocked, profile), pushed)

        stack.pop()
        assertEquals(blocked, split?.page?.screen)
        assertEquals(listOf(feed, SettingsScreen), pushed)
    }

    @Test
    fun activityDefaultsShowsUntilARowIsChosenWithoutBeingOnTheStack() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen)

        val split = assertNotNull(split)
        assertEquals(ActivityDefaultsScreen, split.page.screen)
        assertEquals(split.settings.paneDefault, split.page)
        assertEquals(ActivityDefaultsScreen, split.pane.shownPage)
        assertEquals(listOf(feed, SettingsScreen), screens, "it is not a page opened")

        stack.pop()
        assertEquals(listOf(feed), screens, "back from Settings leaves Settings")
    }

    @Test
    fun onlySettingsHasAPageBesideIt() {
        open(SettingsScreen, PrivacyZonesScreen)

        assertEquals(listOf(null, ActivityDefaultsScreen, null), stack.records.value.map { it.paneDefault?.screen })
    }

    @Test
    fun aUisWidthNeverChangesItsStack() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, PrivacyZonesScreen)
        val before = stack.records.value

        stack.setSize(600.0, TALL)
        assertEquals(before, stack.records.value)
        assertNull(split)
        assertEquals(screens, pushed)

        stack.setSize(1_032.0, TALL)
        assertEquals(before, stack.records.value)
        assertEquals(PrivacyZonesScreen, split?.page?.screen)
    }

    @Test
    fun whatSwiftFollowsChangesWithTheWidthToo() = runTest {
        open(SettingsScreen, PrivacyZonesScreen)
        val seen = mutableListOf<Screen?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { stack.shown.collect { seen += it.split?.page?.screen } }

        stack.setSize(1_032.0, TALL)
        stack.setSize(1_100.0, TALL)
        stack.setSize(600.0, TALL)

        assertEquals(listOf<Screen?>(null, PrivacyZonesScreen, null), seen, "a wider width that changes nothing isn't reported again")
    }

    // "A pane's first page knows it has no way back".

    @Test
    fun onlyThePanesFirstPageIsItsRoot() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen)
        val settings = stack.records.value.last()
        val default = assertNotNull(settings.paneDefault)
        assertTrue(default.paneRoot.value, "Activity defaults, until a row is chosen")
        assertFalse(settings.paneRoot.value)

        open(TrainingZonesScreen, SportZonesScreen("run"))
        val (zones, sport) = stack.records.value.takeLast(2)

        assertTrue(zones.paneRoot.value)
        assertFalse(sport.paneRoot.value, "a deeper page goes back to the first")
        assertFalse(default.paneRoot.value)
    }

    @Test
    fun theSamePageHasAWayBackOnceTheUiIsNarrow() = runTest {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, PrivacyZonesScreen)
        val page = stack.records.value.last()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { page.paneRoot.collect { seen += it } }

        stack.setSize(600.0, TALL)

        assertEquals(listOf(true, false), seen)
        assertFalse(page.paneRoot.value)
    }

    @Test
    fun aScreenUnderAnotherIsNoPanesRoot() {
        stack.setSize(1_032.0, TALL)
        open(SettingsScreen, blocked, profile)

        assertTrue(stack.records.value.none { it.paneRoot.value })
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
