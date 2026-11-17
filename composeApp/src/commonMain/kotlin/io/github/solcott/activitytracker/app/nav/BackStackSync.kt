package io.github.solcott.activitytracker.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator

/**
 * Keeps something outside the app in step with the signed-in back stack, placed by `App` while a session is open: the web app's address bar
 * and history (ui/web-history). The other shells have none.
 */
@Stable
interface BackStackSync {
    @Composable fun Sync(backStack: SaveableBackStack, navigator: Navigator, viewerDid: String)

    /** No sync: Android and desktop. */
    companion object None : BackStackSync {
        @Composable override fun Sync(backStack: SaveableBackStack, navigator: Navigator, viewerDid: String) = Unit
    }
}
