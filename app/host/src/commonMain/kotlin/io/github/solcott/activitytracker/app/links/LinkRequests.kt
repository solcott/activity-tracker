package io.github.solcott.activitytracker.app.links

import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A link to open, and whether it opens on the tab shown whatever tab its page belongs to (a notification's: Record, Account). */
data class LinkRequest(val link: AppLink, val onShownTab: Boolean = false)

/**
 * The links the app was asked to open from outside the UI (ui/deep-links): a tapped https or `activitytracker` link, the web app's URL, a
 * notification. Both shells open [pending] while a session is open, the Compose shell in `App` and the presenter host in `SessionHost`, and
 * mark it [handled]; until then it waits, through sign-in, a restore or a leave. Only the newest waits.
 */
@SingleIn(AppScope::class)
@Inject
class LinkRequests(private val links: AppLinks) {
    /** The link waiting to be opened, or null. */
    val pending: StateFlow<LinkRequest?>
        field = MutableStateFlow<LinkRequest?>(null)

    /** Opens [url] if it is one of the app's links, and says whether it was. */
    fun request(url: String): Boolean {
        val link = links.parse(url) ?: return false
        open(link)
        return true
    }

    /** Opens [link]; with [onShownTab], on the tab shown rather than the one its page belongs to. */
    fun open(link: AppLink, onShownTab: Boolean = false) {
        pending.value = LinkRequest(link, onShownTab)
    }

    /** The recording notification was tapped: Record, over the tab shown (record/gps-recording). */
    fun openRecord() = open(linkOf(RecordScreen), onShownTab = true)

    /** The "export ready" notification was tapped: Settings and its Account page, on the tab shown (auth/data-export). */
    fun openAccount() = open(linkOf(AccountScreen), onShownTab = true)

    private fun linkOf(screen: Screen): AppLink = checkNotNull(AppLinkRoutes.linkOf(screen)) { "$screen has a link" }

    /** Marks [request] opened, unless a newer one has replaced it meanwhile. */
    fun handled(request: LinkRequest) {
        pending.compareAndSet(request, null)
    }
}
