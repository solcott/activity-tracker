package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * How much of the screen's bottom a bar floating over the screens covers: iOS's tab bar, which (like UIKit's) lets content scroll under its
 * glass. `AppShell` provides it; [AppScaffold] pads content clear of it.
 */
val LocalFloatingBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * The screen content an iOS [AppTopBar] blurs behind it once content scrolls under it, like UIKit's bar material. [AppScaffold] provides it
 * on iOS. Elsewhere, and outside an [AppScaffold], it's null and the bar is opaque.
 */
val LocalBarBackdrop = compositionLocalOf<HazeState?> { null }

/**
 * A screen's frame: [topBar] (an [AppTopBar]) over [content], with an optional [bottomBar] pinned under it (an action bar, like a form's
 * Save on Android, desktop and web). [content] gets the padding that clears the bars, including a floating tab bar
 * ([LocalFloatingBottomBarHeight]). Give it to a list or scrolling column as its content padding, inside the scroll, so content starts
 * clear of the bars but scrolls under them. On iOS the top bar blurs the content scrolled under it ([LocalBarBackdrop]).
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberBarBackdrop()
    CompositionLocalProvider(LocalBarBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = LocalFloatingBottomBarHeight.current)),
        ) { padding ->
            // The source includes the page background, so the bar's edge effect fades into it with no fill of its own.
            val background = MaterialTheme.colorScheme.background
            Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.background(background).hazeSource(it) } ?: Modifier)) {
                content(padding)
            }
        }
    }
}

/** The [LocalBarBackdrop] for an [AppScaffold]: a fresh Haze state on iOS, where the top bar blurs content under it; null elsewhere. */
@Composable internal expect fun rememberBarBackdrop(): HazeState?
