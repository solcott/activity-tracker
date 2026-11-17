package io.github.solcott.activitytracker.app.nav

import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.links.AppLinks
import io.github.solcott.activitytracker.app.links.LinkOpener
import io.github.solcott.activitytracker.app.links.LinkRequest
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The Compose shell opens a link as the presenter host does (ui/deep-links, dev/presenter-host "The host opens links on its stacks"): its
 * tab, saving the one shown, and its chain kept up to what the stack already holds.
 */
class LinkThroughShellTest {
    private val viewer = "did:plc:alice"
    private val segment = "at://did:plc:carol/app.activitytracker.feed.segment/3kseg"
    private val backStack = SaveableBackStack(FeedScreen)
    private val navigator = StackNavigator(backStack)
    private val opener = LinkOpener(viewer) { null }
    private val links = AppLinks(null)

    private val screens: List<Screen>
        get() = backStack.map { it.screen }.asReversed()

    private suspend fun open(url: String, onShownTab: Boolean = false) {
        val link = opener.open(LinkRequest(links.parse(url)!!, onShownTab))!!
        openLink(backStack, navigator, viewer, link)
    }

    @Test
    fun anOwnSettingsPageOpensOnYouAndTheTabBeforeKeepsItsPlace() = runTest {
        selectTab(backStack, navigator, viewer, TopLevelDestination.Search, toRoot = true)
        navigator.goTo(SegmentScreen(segment))

        open("/settings/training-zones/history")

        assertEquals(listOf(ProfileScreen(viewer), SettingsScreen, TrainingZonesScreen, ThresholdHistoryScreen), screens)
        selectTab(backStack, navigator, viewer, TopLevelDestination.Search, toRoot = true)
        assertEquals(listOf(AthleteSearchScreen, SegmentScreen(segment)), screens, "Search kept its segment")
    }

    @Test
    fun aLinkKeepsWhatTheStackHoldsOfItsChain() = runTest {
        navigator.goTo(SegmentScreen(segment))

        open("/segment/did:plc:carol/3kseg/leaderboard")

        assertEquals(listOf(FeedScreen, SegmentScreen(segment), SegmentLeaderboardScreen(segment)), screens)
    }

    @Test
    fun aNotificationsRequestOpensOnTheTabShown() = runTest {
        navigator.goTo(SettingsScreen)
        navigator.goTo(TrainingZonesScreen)

        open("/settings/account", onShownTab = true)

        assertEquals(listOf(FeedScreen, SettingsScreen, AccountScreen), screens)
    }

    @Test
    fun choosingTheTabShownReturnsItToItsFirstScreenButALinkDoesNot() = runTest {
        navigator.goTo(SegmentScreen(segment))
        selectTab(backStack, navigator, viewer, TopLevelDestination.Feed, toRoot = false)
        assertEquals(listOf(FeedScreen, SegmentScreen(segment)), screens)
        selectTab(backStack, navigator, viewer, TopLevelDestination.Feed, toRoot = true)
        assertEquals(listOf<Screen>(FeedScreen), screens)
    }

    /** Circuit's navigator over [stack], as `rememberCircuitNavigator` makes it, without a composition. */
    private class StackNavigator(private val stack: SaveableBackStack) : Navigator {
        override fun goTo(screen: Screen): Boolean = if (stack.topRecord?.screen == screen) false else stack.push(screen)

        override fun forward(): Boolean = false

        override fun backward(): Boolean = false

        override fun pop(result: PopResult?): Screen? = if (stack.size > 1) stack.pop()?.screen else null

        override fun peek(): Screen? = stack.topRecord?.screen

        override fun peekBackStack(): List<Screen> = stack.map { it.screen }

        override fun peekNavStack(): NavStackList<Screen>? = null

        override fun resetRoot(newRoot: Screen, options: Navigator.StateOptions): List<Screen> {
            val old = peekBackStack()
            if (options.save) stack.saveState()
            if (!(options.restore && stack.restoreState(newRoot))) {
                while (stack.size > 0) stack.pop()
                stack.push(newRoot)
            }
            return old
        }
    }
}
