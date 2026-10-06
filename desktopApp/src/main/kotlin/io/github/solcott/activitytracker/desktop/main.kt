package io.github.solcott.activitytracker.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
import java.awt.Dimension
import java.net.URI
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

fun main() {
    val port = System.getProperty("activitytracker.oauthPort")?.toInt() ?: NetworkConfigs.DESKTOP_OAUTH_PORT
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(DesktopAppDirs.forCurrentOs(), NetworkConfigs.desktop(port))
    val redirects = LoopbackRedirectServer(port, graph.authCoordinator).apply { start() }

    application {
        Window(
            title = stringResource(Res.string.app_name),
            // Room for the sidebar layout; narrower windows switch to the rail, then the bottom bar.
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            onCloseRequest = {
                redirects.stop()
                exitApplication()
            },
        ) {
            redirects.window = window
            window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
            // MapLibre draws into this window's graphics context.
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) { App(graph) }
        }
    }
}

/** About a phone's size, below which the layout stops working. */
private const val MIN_WIDTH = 400
private const val MIN_HEIGHT = 600

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
