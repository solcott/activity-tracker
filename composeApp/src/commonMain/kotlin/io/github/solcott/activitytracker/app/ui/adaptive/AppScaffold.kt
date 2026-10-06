package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How much of the screen's bottom a bar floating over the screens covers: iOS's tab bar, which (like UIKit's) lets content scroll under its
 * glass. `AppShell` provides it; [AppScaffold] pads content clear of it.
 */
val LocalFloatingBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * A screen's frame: [topBar] (an [AppTopBar]) over [content]. [content] gets the padding that clears the bars, including a floating tab bar
 * ([LocalFloatingBottomBarHeight]). Give it to a list or scrolling column as its content padding, inside the scroll, so content starts
 * clear of the bars but scrolls under them.
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = LocalFloatingBottomBarHeight.current)),
        content = content,
    )
}
