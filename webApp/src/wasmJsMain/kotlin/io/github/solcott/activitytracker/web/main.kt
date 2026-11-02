package io.github.solcott.activitytracker.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.github.solcott.activitytracker.app.App
import io.github.solcott.activitytracker.app.NetworkConfigs
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.app_name
import io.github.solcott.activitytracker.data.auth.parseRedirectParams
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    // The OAuth loopback client only allows 127.0.0.1 redirect URIs.
    if (window.location.hostname == "localhost") {
        window.location.replace(window.location.href.replaceFirst("//localhost", "//127.0.0.1"))
        return
    }
    val graph = createGraph<WebAppGraph>()

    // Returning from the authorization server: finish sign-in, then strip the params from the URL.
    val params = parseRedirectParams(window.location.href)
    if ("state" in params && ("code" in params || "error" in params)) {
        window.history.replaceState(null, "", window.location.pathname)
        MainScope().launch { graph.authCoordinator.handleRedirect(params) }
    }

    // index.html's <title> only covers loading; the shared string takes over once the app starts.
    MainScope().launch { document.title = getString(Res.string.app_name) }
    ComposeViewport(document.body!!) {
        // Uploads go on only while the tab is open: closing it mid-import asks first, unless the athlete paused imports
        // (data/upload-queue).
        App(graph, onImportsActive = { active -> window.onbeforeunload = if (active) ::askBeforeUnload else null })
    }
    // index.html's spinner covers the wasm download; the app draws from here.
    document.getElementById("loading")?.remove()
}

/** Asks the browser to confirm leaving: it shows its own message, whatever is returned. */
private fun askBeforeUnload(event: Event): String {
    event.preventDefault()
    return ""
}

@DependencyGraph(AppScope::class)
interface WebAppGraph : AppGraph {
    @Provides fun atprotoConfig(): AtprotoConfig = NetworkConfigs.web(window.location.origin)
}
