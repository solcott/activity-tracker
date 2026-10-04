package io.github.solcott.activitytracker.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.solcott.activitytracker.app.App
import io.github.solcott.activitytracker.app.NetworkConfigs
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.app_name
import io.github.solcott.activitytracker.data.auth.AuthBrowser
import io.github.solcott.activitytracker.data.di.DesktopAppDirs
import java.awt.Desktop
import java.net.URI
import org.jetbrains.compose.resources.stringResource

fun main() {
    val port = System.getProperty("activitytracker.oauthPort")?.toInt() ?: NetworkConfigs.DESKTOP_OAUTH_PORT
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(DesktopAppDirs.forCurrentOs(), NetworkConfigs.desktop(port))
    val redirects = LoopbackRedirectServer(port, graph.authCoordinator).apply { start() }

    application {
        Window(
            title = stringResource(Res.string.app_name),
            onCloseRequest = {
                redirects.stop()
                exitApplication()
            },
        ) {
            redirects.window = window
            App(graph)
        }
    }
}

@DependencyGraph(AppScope::class)
interface DesktopAppGraph : AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides dirs: DesktopAppDirs, @Provides atprotoConfig: AtprotoConfig): DesktopAppGraph
    }
}

/** Opens OAuth in the system browser; the redirect comes back to [LoopbackRedirectServer]. */
@ContributesBinding(AppScope::class)
@Inject
class DesktopAuthBrowser : AuthBrowser {
    override fun open(url: String) = Desktop.getDesktop().browse(URI(url))
}
