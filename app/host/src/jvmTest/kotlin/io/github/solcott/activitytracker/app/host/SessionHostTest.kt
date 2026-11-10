package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.SessionMode
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeftElsewhereScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A signed-in account's screens run with no UI (dev/presenter-host). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionHostTest {
    private val fakes = FakePresenters()
    private val steps = OpenSteps()
    private val recordRequests = RecordScreenRequests()
    private val accountRequests = AccountScreenRequests()
    private var recovered = false
    private val host =
        SessionHost(
            session = sessionOf("did:plc:alice"),
            factories = fakes.factories,
            runner = SessionRunner(steps),
            recording = { recovered },
            recordRequests = recordRequests,
            accountRequests = accountRequests,
        )
    private val feed = host.stack(TopLevelDestination.Feed)
    private val search = host.stack(TopLevelDestination.Search)
    private val activity = TestScreen("activity")

    private fun TestScope.start() {
        backgroundScope.launch { host.run(HostClock.Immediate) }
        runCurrent()
    }

    private val TabStack.top
        get() = records.value.last()

    private val TabStack.screens
        get() = records.value.map { it.screen }

    @Test
    fun theAthletesUnitsAreTheSessions() {
        assertEquals(UnitSystem.Metric, host.shownUnits.value.units)

        steps.units.value = UnitSystem.Imperial

        assertEquals(UnitSystem.Imperial, host.shownUnits.value.units)
    }

    @Test
    fun aShownScreenPublishesStatesAndTakesEvents() = runTest {
        start()
        assertEquals(SessionMode.Open, host.mode.value)
        assertEquals(0, feed.top.test.bumps)

        feed.top.test.eventSink(TestEvent.Bump)
        runCurrent()

        assertEquals(1, feed.top.test.bumps)
    }

    @Test
    fun textSetFromOutsideReachesThePresenter() = runTest {
        start()

        feed.top.test.field.setTextAndPlaceCursorAtEnd("alice.test")
        runCurrent()

        assertEquals("alice.test", feed.top.test.text)
    }

    @Test
    fun aPresenterNavigatesItsOwnTabsStack() = runTest {
        start()

        feed.top.test.eventSink(TestEvent.GoTo(activity))
        runCurrent()

        assertEquals(listOf(FeedScreen, activity), feed.screens)
        assertEquals(listOf<Any>(AthleteSearchScreen), search.screens)
        assertEquals(activity, feed.top.test.screen)

        feed.top.test.eventSink(TestEvent.Pop)
        runCurrent()
        assertEquals(listOf<Any>(FeedScreen), feed.screens)
    }

    @Test
    fun onlyTheTopScreenOfTheChosenTabRuns() = runTest {
        start()
        assertEquals(listOf<Any>(FeedScreen), fakes.running)

        feed.goTo(activity)
        runCurrent()

        assertEquals(listOf<Any>(activity), fakes.running)
        assertNotNull(feed.records.value.first().state.value, "a covered screen's last state stays readable")
    }

    @Test
    fun aCoveredScreenKeepsWhatItRetainedAndSaved() = runTest {
        start()
        val before = feed.top.test

        feed.goTo(activity)
        runCurrent()
        feed.pop()
        runCurrent()

        assertEquals(listOf<Any>(FeedScreen), fakes.running)
        assertEquals(before.retained, feed.top.test.retained)
        assertEquals(before.saved, feed.top.test.saved)
    }

    @Test
    fun aScreenOpenedAgainStartsWithNothingKept() = runTest {
        start()
        feed.goTo(activity)
        runCurrent()
        val first = feed.top.test

        feed.pop()
        runCurrent()
        feed.goTo(activity)
        runCurrent()

        assertNotEquals(first.retained, feed.top.test.retained)
        assertNotEquals(first.saved, feed.top.test.saved)
    }

    @Test
    fun aScreenClosedWhileCoveredIsForgottenToo() = runTest {
        start()
        feed.goTo(activity)
        runCurrent()
        val first = feed.top.test
        feed.goTo(TestScreen("edit"))
        runCurrent()

        // Back two screens in one gesture, then the same activity again.
        feed.popTo(1)
        runCurrent()
        feed.goTo(activity)
        runCurrent()

        assertNotEquals(first.retained, feed.top.test.retained)
    }

    @Test
    fun aScreenTheUiReportsVisibleRunsWhileCovered() = runTest {
        start()
        feed.goTo(activity)
        runCurrent()
        val root = feed.records.value.first()

        // Part-way through a back swipe, the feed shows beneath the activity.
        root.setVisible(true)
        runCurrent()
        assertEquals(setOf<Any>(FeedScreen, activity), fakes.running.toSet())

        root.setVisible(false)
        runCurrent()
        assertEquals(listOf<Any>(activity), fakes.running)
    }

    // "The page shown before a row is chosen is not on the stack".

    @Test
    fun thePageBesideSettingsRunsOnlyWhileTheUiShowsIt() = runTest {
        start()
        feed.setSize(1_032.0, TALL)
        feed.goTo(SettingsScreen)
        runCurrent()
        val beside = assertNotNull(feed.top.paneDefault)

        assertEquals(listOf<Any>(SettingsScreen), fakes.running, "nothing draws it yet")
        assertNull(beside.state.value)

        beside.setVisible(true)
        runCurrent()
        assertEquals(setOf<Any>(SettingsScreen, ActivityDefaultsScreen), fakes.running.toSet())
        assertEquals(ActivityDefaultsScreen, beside.test.screen)
        assertEquals(listOf(FeedScreen, SettingsScreen), feed.screens, "it is not on the stack")

        beside.setVisible(false)
        runCurrent()
        assertEquals(listOf<Any>(SettingsScreen), fakes.running)
    }

    @Test
    fun thePageBesideSettingsKeepsItsStateWhileSettingsIsOnTheStack() = runTest {
        start()
        feed.setSize(1_032.0, TALL)
        feed.goTo(SettingsScreen)
        val beside = assertNotNull(feed.top.paneDefault)
        beside.setVisible(true)
        runCurrent()
        val first = beside.test

        // A row opens its page in place of it, and going back to the list shows it again.
        beside.setVisible(false)
        feed.goTo(AccountScreen)
        runCurrent()
        feed.pop()
        beside.setVisible(true)
        runCurrent()

        assertEquals(first.retained, beside.test.retained)
        assertEquals(first.saved, beside.test.saved)
    }

    @Test
    fun settingsOpenedAgainGetsANewPageBesideIt() = runTest {
        start()
        feed.goTo(SettingsScreen)
        val first = assertNotNull(feed.top.paneDefault)
        first.setVisible(true)
        runCurrent()
        val retained = first.test.retained

        feed.pop()
        runCurrent()
        assertEquals(listOf<Any>(FeedScreen), fakes.running, "it left with Settings, though the UI never said it was hidden")
        feed.goTo(SettingsScreen)
        val second = assertNotNull(feed.top.paneDefault)
        second.setVisible(true)
        runCurrent()

        assertNotEquals(first.key, second.key)
        assertNotEquals(retained, second.test.retained)
    }

    @Test
    fun choosingATabRunsItsTopScreenAndStopsTheOthers() = runTest {
        start()
        feed.goTo(activity)
        runCurrent()

        host.select(TopLevelDestination.Search)
        runCurrent()
        assertEquals(TopLevelDestination.Search, host.selected.value)
        assertEquals(listOf<Any>(AthleteSearchScreen), fakes.running)

        host.select(TopLevelDestination.Feed)
        runCurrent()
        assertEquals(listOf<Any>(activity), fakes.running, "the tab kept its place")
        assertEquals(listOf(FeedScreen, activity), feed.screens)
    }

    @Test
    fun whatSwiftFollowsMatchesTheModeAndTheChosenTab() = runTest {
        start()
        assertEquals(SessionMode.Open, host.shownMode.value.mode)
        assertEquals(TopLevelDestination.Feed, host.shownTab.value.destination)

        host.select(TopLevelDestination.You)
        steps.left.value = true
        runCurrent()

        assertEquals(SessionMode.LeftElsewhere, host.shownMode.value.mode)
        assertEquals(host.selected.value, host.shownTab.value.destination)
    }

    @Test
    fun choosingTheTabOnScreenReturnsItToItsFirstScreen() = runTest {
        start()
        feed.goTo(activity)
        feed.goTo(TestScreen("edit"))
        runCurrent()

        host.select(TopLevelDestination.Feed)
        runCurrent()

        assertEquals(listOf<Any>(FeedScreen), feed.screens)
        assertEquals(listOf<Any>(FeedScreen), fakes.running)
    }

    @Test
    fun aRecordRequestOpensRecordOnTheTabOnScreenAndIsMarkedHandled() = runTest {
        start()
        host.select(TopLevelDestination.Search)
        runCurrent()

        recordRequests.request()
        runCurrent()

        assertEquals(listOf(AthleteSearchScreen, RecordScreen), search.screens)
        assertEquals(listOf<Any>(FeedScreen), feed.screens)
        assertTrue(recordRequests.requests.replayCache.isEmpty(), "a new session doesn't replay it")
    }

    @Test
    fun aRecordRequestMadeBeforeTheSessionOpensWaitsForIt() = runTest {
        recordRequests.request()
        start()

        assertEquals(listOf(FeedScreen, RecordScreen), feed.screens)
    }

    @Test
    fun anAccountRequestOpensSettingsThenAccount() = runTest {
        start()

        accountRequests.request()
        runCurrent()

        assertEquals(listOf(FeedScreen, SettingsScreen, AccountScreen), feed.screens)
    }

    @Test
    fun anUnfinishedRecordingOpensRecordOverTheFeed() = runTest {
        recovered = true
        start()

        assertEquals(listOf(FeedScreen, RecordScreen), feed.screens)
        assertEquals(false, feed.top.showsTabs)
    }

    @Test
    fun nothingRunsUntilTheModeIsKnown() = runTest {
        steps.leavingHere.value = null
        start()

        assertEquals(SessionMode.Unknown, host.mode.value)
        assertTrue(fakes.running.isEmpty())
        assertNull(feed.top.state.value)
    }

    @Test
    fun aLeaveHereRunsOnlyTheLeave() = runTest {
        steps.leavingHere.value = true
        start()

        assertEquals(SessionMode.Leaving, host.mode.value)
        assertEquals(listOf<Any>(LeaveScreen(resume = true)), fakes.running)
        assertTrue(host.leaving.isRoot)
    }

    @Test
    fun leavingElsewhereShowsTheChoiceAndComingBackStartsFromTheFeed() = runTest {
        start()
        host.select(TopLevelDestination.Search)
        search.goTo(activity)
        runCurrent()

        steps.left.value = true
        runCurrent()
        assertEquals(SessionMode.LeftElsewhere, host.mode.value)
        assertEquals(listOf<Any>(LeftElsewhereScreen), fakes.running)

        steps.left.value = false
        runCurrent()
        assertEquals(TopLevelDestination.Feed, host.selected.value)
        assertEquals(listOf<Any>(AthleteSearchScreen), search.screens)
        assertEquals(listOf<Any>(FeedScreen), fakes.running)
    }
}

/** Steps for a session with no leave anywhere, so the app opens at once; tests flip the two flags. */
private class OpenSteps : SessionSteps {
    val leavingHere = MutableStateFlow<Boolean?>(false)
    override val left = MutableStateFlow(false)

    override val leaving: Flow<Boolean>
        get() = leavingHere.filterNotNull()

    override suspend fun resolveSpacesSupport() = Unit

    override val units = MutableStateFlow(UnitSystem.Metric)

    override suspend fun refreshUnits() = Unit

    override suspend fun checkStillHere() = Unit

    override suspend fun runWork() {
        awaitCancellation()
    }
}
