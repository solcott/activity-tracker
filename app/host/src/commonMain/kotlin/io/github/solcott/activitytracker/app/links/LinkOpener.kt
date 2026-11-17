package io.github.solcott.activitytracker.app.links

import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Handle

/** Where a link goes for the signed-in viewer: [tab], or the tab shown when null, and the [chain] pushed there (ui/deep-links). */
data class OpenedLink(val tab: TopLevelDestination?, val chain: List<Screen>)

/** A handle's DID, or null when it doesn't resolve. */
fun interface HandleResolution {
    suspend fun did(handle: String): String?
}

/** [HandleResolution] over the app's identity resolver. */
@ContributesBinding(AppScope::class)
@Inject
class IdentityHandleResolution(private val identity: IdentityResolver) : HandleResolution {
    override suspend fun did(handle: String): String? = runSuspendCatching { identity.resolveHandle(Handle(handle)).value }.getOrNull()
}

/**
 * Reads a link for the signed-in account [viewer] (a DID, ui/deep-links): which tab and stack it opens, resolving a handle in it to its DID
 * and opening an owner's form only for its owner. Both shells ask it, so a link builds the same stack in each.
 */
class LinkOpener(private val viewer: String, private val handles: HandleResolution) {
    /** What [request] opens, or null when no page has its path. */
    suspend fun open(request: LinkRequest): OpenedLink? {
        val plan = AppLinkRoutes.plan(request.link, viewer, handles::did) ?: return null
        val tab =
            when {
                request.onShownTab -> null
                plan.tab == LinkTab.Feed -> TopLevelDestination.Feed
                plan.tab == LinkTab.Search -> TopLevelDestination.Search
                plan.tab == LinkTab.You -> TopLevelDestination.You
                else -> null
            }
        return OpenedLink(tab, plan.chain)
    }
}
