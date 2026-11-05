package io.github.solcott.activitytracker.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavDecoration
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavArgument
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.navigation.navStackListOf
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.LocalSettingsPane
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsPaneRoot
import kotlin.test.Test
import kotlin.test.assertEquals

/** How [SettingsListDetailDecoration] lays out each stack shape at each width (ui/settings-layout). */
@OptIn(ExperimentalTestApi::class)
class SettingsListDetailDecorationTest {
    private data class Arg(override val screen: Screen) : NavArgument {
        override val key: String = screen.toString()
    }

    /** Stands in for the gesture decoration: draws only the top of the stack it's given, and keeps that stack. */
    private class TopOnly : NavDecoration {
        var seen: List<Screen> = emptyList()

        @Composable
        override fun <T : NavArgument> DecoratedContent(
            args: NavStackList<T>,
            navigator: Navigator,
            modifier: Modifier,
            content: @Composable (T) -> Unit,
        ) {
            seen = args.map { it.screen }
            Box(modifier) { content(args.top) }
        }
    }

    private val wide = 1000.dp
    private val narrow = 500.dp

    /** Each record draws its screen and whether it's a pane's first page, and counts how often it's composed afresh. */
    private val creations = mutableMapOf<String, Int>()

    @Composable
    private fun Record(arg: Arg) {
        remember { creations[arg.key] = (creations[arg.key] ?: 0) + 1 }
        val pane = LocalSettingsPane.current
        Column {
            Text("record ${arg.screen}${if (LocalIsPaneRoot.current) " (pane root)" else ""}")
            if (arg.screen == SettingsScreen && pane.twoPane) Text("marks ${pane.shownPage}")
        }
    }

    private fun stack(vararg topFirst: Screen) = checkNotNull(navStackListOf(topFirst.map(::Arg)))

    /** What a laid-out stack shows: the stack [seen] by the delegate, and text that [shows] or [hides]. */
    private class Laid(val seen: List<Screen>, val shows: (String) -> Unit, val hides: (String) -> Unit)

    private fun layOut(width: Dp, args: NavStackList<Arg>, check: Laid.() -> Unit) = runComposeUiTest {
        val delegate = TopOnly()
        val circuit = Circuit.Builder().setOnUnavailableContent { screen, _ -> Text("shown $screen") }.build()
        setContent {
            CircuitCompositionLocals(circuit) {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    SettingsListDetailDecoration(delegate).DecoratedContent(args, Navigator.NoOp, Modifier) { Record(it) }
                }
            }
        }
        waitForIdle()
        Laid(
                delegate.seen,
                shows = { onNodeWithText(it, substring = true).assertExists() },
                hides = { onNodeWithText(it, substring = true).assertDoesNotExist() },
            )
            .check()
    }

    @Test
    fun wideSettingsAloneShowsActivityDefaultsBesideTheList() =
        layOut(wide, stack(SettingsScreen, ProfileScreen("alice"), FeedScreen)) {
            assertEquals(listOf(SettingsScreen, ProfileScreen("alice"), FeedScreen), seen)
            shows("record $SettingsScreen")
            shows("shown ActivityDefaultsScreen")
            shows("marks ActivityDefaultsScreen")
        }

    @Test
    fun wideWithAPageShowsItBesideTheListWithoutBack() =
        layOut(wide, stack(PrivacyZonesScreen, SettingsScreen, FeedScreen)) {
            assertEquals(listOf(SettingsScreen, FeedScreen), seen, "the delegate's top is Settings")
            shows("record $SettingsScreen")
            shows("record $PrivacyZonesScreen (pane root)")
            shows("marks $PrivacyZonesScreen")
        }

    @Test
    fun wideWithTheAccountPageShowsItBesideTheListWithoutBack() =
        layOut(wide, stack(AccountScreen, SettingsScreen, FeedScreen)) {
            assertEquals(listOf(SettingsScreen, FeedScreen), seen)
            shows("record $AccountScreen (pane root)")
            shows("marks $AccountScreen")
        }

    @Test
    fun wideWithASubPageShowsItInThePaneWithBack() =
        layOut(wide, stack(SportZonesScreen("run"), TrainingZonesScreen, SettingsScreen, FeedScreen)) {
            assertEquals(listOf(SettingsScreen, FeedScreen), seen)
            shows("record ${SportZonesScreen("run")}")
            hides("(pane root)")
            hides("record $TrainingZonesScreen")
            shows("marks $TrainingZonesScreen")
        }

    @Test
    fun aProfileOverAPageGoesFullWidth() =
        layOut(wide, stack(ProfileScreen("bob"), PrivacyZonesScreen, SettingsScreen, FeedScreen)) {
            assertEquals(listOf(ProfileScreen("bob"), PrivacyZonesScreen, SettingsScreen, FeedScreen), seen, "passed through")
            shows("record ${ProfileScreen("bob")}")
            hides("record $SettingsScreen")
        }

    @Test
    fun narrowPassesThrough() =
        layOut(narrow, stack(PrivacyZonesScreen, SettingsScreen, FeedScreen)) {
            assertEquals(listOf(PrivacyZonesScreen, SettingsScreen, FeedScreen), seen)
            shows("record $PrivacyZonesScreen")
            hides("record $SettingsScreen")
            hides("(pane root)")
        }

    @Test
    fun narrowSettingsAloneShowsNoPage() =
        layOut(narrow, stack(SettingsScreen, FeedScreen)) {
            shows("record $SettingsScreen")
            hides("shown ActivityDefaultsScreen")
        }

    /** Lays [args] out in a box whose width the test changes, drawing records with [Record]. */
    private fun resize(args: NavStackList<Arg>, from: Dp, to: Dp, check: () -> Unit) = runComposeUiTest {
        var width by mutableStateOf(from)
        val circuit = Circuit.Builder().setOnUnavailableContent { screen, _ -> Text("shown $screen") }.build()
        setContent {
            CircuitCompositionLocals(circuit) {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    SettingsListDetailDecoration(TopOnly()).DecoratedContent(args, Navigator.NoOp, Modifier) { Record(it) }
                }
            }
        }
        waitForIdle()
        width = to
        waitForIdle()
        check()
    }

    @Test
    fun narrowingWithAPageOpenKeepsThePage() =
        resize(stack(PrivacyZonesScreen, SettingsScreen, FeedScreen), from = wide, to = narrow) {
            assertEquals(1, creations[PrivacyZonesScreen.toString()], "the page moved to fill the window, not composed again")
        }

    @Test
    fun wideningFromTheListKeepsTheList() =
        resize(stack(SettingsScreen, FeedScreen), from = narrow, to = wide) {
            assertEquals(1, creations[SettingsScreen.toString()], "the list moved into its pane, not composed again")
        }
}
