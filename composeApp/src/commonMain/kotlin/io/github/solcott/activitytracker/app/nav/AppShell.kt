package io.github.solcott.activitytracker.app.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.popUntil
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.app_name
import org.jetbrains.compose.resources.stringResource

/**
 * The signed-in app's navigation chrome around [content] (the navigable screens): Material's navigation bar on phones and narrow windows,
 * its rail on tablets and mid-width windows; and a sidebar on wide desktop and web windows. Full-screen flows
 * ([TopLevelDestination.showsNavigation]) hide it.
 *
 * Choosing a tab swaps in its back stack (saving the current one), so each tab keeps its place. Choosing the current tab again goes back to
 * its root.
 */
@Composable
fun AppShell(
    backStack: SaveableBackStack,
    navigator: Navigator,
    viewerDid: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val platform = LocalUiPlatform.current
    val root = backStack.rootRecord?.screen
    val current = root?.let { TopLevelDestination.of(it, viewerDid) }
    val showNavigation = backStack.topRecord?.screen?.let(TopLevelDestination::showsNavigation) ?: false
    val select: (TopLevelDestination) -> Unit = { destination ->
        if (destination == current) {
            navigator.popUntil { it == root }
        } else {
            navigator.resetRoot(destination.screen(viewerDid), Navigator.StateOptions(save = true, restore = true))
        }
    }

    // Moves (rather than recreates) the screens when a resize swaps the layout.
    val screens = remember(content) { movableContentOf(content) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val style =
            when {
                !showNavigation -> null
                maxWidth < COMPACT_MAX -> NavigationStyle.Bar
                platform.hasPointer && maxWidth >= SIDEBAR_MIN -> NavigationStyle.Sidebar
                else -> NavigationStyle.Rail
            }
        when (style) {
            null -> screens(Modifier.fillMaxSize())
            NavigationStyle.Bar ->
                Column(Modifier.fillMaxSize()) {
                    // The bar pads itself clear of the home indicator / gesture area, so screens above it mustn't.
                    screens(Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                    BottomBar(current, select)
                }
            NavigationStyle.Rail ->
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)) {
                        Spacer(Modifier.height(8.dp))
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == current,
                                onClick = { select(destination) },
                                icon = { PlatformIcon(destination.icon, null, filled = destination == current) },
                                label = { Text(destination.title.string()) },
                                modifier = Modifier.handCursor(),
                            )
                        }
                    }
                    screens(Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)))
                }
            NavigationStyle.Sidebar ->
                Row(Modifier.fillMaxSize()) {
                    Sidebar(current, select)
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    screens(Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)))
                }
        }
    }
}

private enum class NavigationStyle {
    Bar,
    Rail,
    Sidebar,
}

/** Material's navigation bar. */
@Composable
private fun BottomBar(current: TopLevelDestination?, select: (TopLevelDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { select(destination) },
                icon = { PlatformIcon(destination.icon, null, filled = destination == current) },
                label = { Text(destination.title.string()) },
                modifier = Modifier.handCursor(),
            )
        }
    }
}

/** A web-app sidebar: the app's name, then the destinations. */
@Composable
private fun Sidebar(current: TopLevelDestination?, select: (TopLevelDestination) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.width(SIDEBAR_WIDTH).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 20.dp),
            )
            TopLevelDestination.entries.forEach { destination ->
                NavigationDrawerItem(
                    label = { Text(destination.title.string()) },
                    selected = destination == current,
                    onClick = { select(destination) },
                    icon = { PlatformIcon(destination.icon, null, filled = destination == current) },
                    modifier = Modifier.handCursor(),
                )
            }
            Box(Modifier.weight(1f))
        }
    }
}

/** Material's compact window class: phones, and narrow desktop or browser windows. */
private val COMPACT_MAX = 600.dp

/** Material's expanded window class, where a pointer platform has room for a permanent sidebar. */
private val SIDEBAR_MIN = 840.dp
private val SIDEBAR_WIDTH = 240.dp
