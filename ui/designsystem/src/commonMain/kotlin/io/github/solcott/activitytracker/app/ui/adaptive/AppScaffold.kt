package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A screen's frame: [topBar] (an [AppTopBar]) over [content], with an optional [bottomBar] pinned under it (an action bar, like a form's
 * Save on Android, desktop and web). [content] gets the padding that clears the bars. Give it to a list or scrolling column as its content
 * padding, inside the scroll, so content starts clear of the bars but scrolls under them.
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Box(Modifier.fillMaxSize()) { content(padding) }
    }
}
