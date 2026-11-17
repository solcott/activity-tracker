package io.github.solcott.activitytracker.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.links.AppLinkRoutes
import io.github.solcott.activitytracker.app.links.LinkRequests
import io.github.solcott.activitytracker.app.nav.BackStackSync
import io.github.solcott.activitytracker.app.nav.HistoryEntry
import io.github.solcott.activitytracker.app.nav.HistoryMirror
import io.github.solcott.activitytracker.app.nav.HistoryOp
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.nav.selectTab
import kotlinx.browser.window
import kotlinx.coroutines.flow.first
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

/**
 * The address bar and the browser's history follow the signed-in stack, and Back and Forward walk it (ui/web-history). The pure rules are
 * [HistoryMirror]'s; this writes them to `window.history` and answers `popstate`. Each entry's state is its place in the history, so the
 * mirror knows where Back or Forward landed. Nothing is written until the link the page was loaded with has opened, so a loaded URL builds
 * its stack first and then gets an entry per screen.
 */
@OptIn(ExperimentalWasmJsInterop::class)
class BrowserHistorySync(private val links: LinkRequests) : BackStackSync {
    @Composable
    override fun Sync(backStack: SaveableBackStack, navigator: Navigator, viewerDid: String) {
        val mirror = remember(backStack) { HistoryMirror() }
        val started = remember(backStack) { BooleanArray(1) }

        LaunchedEffect(backStack) {
            links.pending.first { it == null }
            snapshotFlow { entriesOf(backStack, viewerDid) }
                .collect { stack ->
                    val ops = if (started[0]) mirror.show(stack) else mirror.start(stack, at = currentPlace()).also { started[0] = true }
                    ops.forEach(::write)
                }
        }

        DisposableEffect(backStack) {
            val onPopState: (Event) -> Unit = { event ->
                val place = (event as? PopStateEvent)?.state?.toString()?.toIntOrNull()
                if (started[0] && place != null) landed(mirror, place, backStack, navigator, viewerDid)
            }
            window.addEventListener("popstate", onPopState)
            onDispose {
                window.removeEventListener("popstate", onPopState)
                // Signed out, another account, or a leave: the URL goes back to the start.
                window.history.replaceState(null, "", "/")
            }
        }
    }

    /** Back or Forward landed on the entry at [place]: show its record, or open the URL as a link when the stack no longer holds it. */
    private fun landed(mirror: HistoryMirror, place: Int, backStack: SaveableBackStack, navigator: Navigator, viewerDid: String) {
        val entry = mirror.landed(place)
        if (entry == null) {
            links.request(window.location.pathname + window.location.search)
            return
        }
        entry.tab?.let { selectTab(backStack, navigator, viewerDid, it, toRoot = false) }
        if (backStack.any { it.key == entry.key }) {
            while (backStack.topRecord?.key != entry.key) navigator.pop()
        } else {
            mirror.takeOver()
            links.request(entry.path)
        }
    }

    private fun entriesOf(backStack: SaveableBackStack, viewerDid: String): List<HistoryEntry> {
        val tab = backStack.rootRecord?.screen?.let { TopLevelDestination.of(it, viewerDid) }
        // The stack lists its top first.
        return backStack.toList().asReversed().map { record ->
            HistoryEntry(record.key, tab, AppLinkRoutes.linkOf(record.screen)?.path ?: "/")
        }
    }

    private fun write(op: HistoryOp) {
        when (op) {
            is HistoryOp.Push -> window.history.pushState(op.index.toString().toJsString(), "", op.entry.path)
            is HistoryOp.Replace -> window.history.replaceState(op.index.toString().toJsString(), "", op.entry.path)
            is HistoryOp.Back -> window.history.go(-op.steps)
        }
    }

    /** The place of the entry the page was loaded on: kept by a reload, else the first. */
    private fun currentPlace(): Int = window.history.state?.toString()?.toIntOrNull() ?: 0
}
