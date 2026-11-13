package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.di.SessionGraph
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.RootState
import io.github.solcott.activitytracker.app.host.startOnMain
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.photoCid
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

/**
 * The iOS app's screens, run by the presenter host with no UI (dev/presenter-host). `iosApp`'s SwiftUI shell follows the host and draws
 * every screen natively (ui/ios-native-shell).
 *
 * Started by the first caller. Main thread only.
 */
internal object IosHost {
    private var graph: IosAppGraph? = null

    /** The signed-in account's graph, whose uploads hold the photos this device sent; null when signed out. */
    var session: SessionGraph? = null
        private set

    fun start(graph: IosAppGraph): AppHost {
        if (this.graph != null) return graph.appHost
        this.graph = graph
        graph.appHost.startOnMain { oauth -> graph.createSessionGraph(oauth).also { session = it }.sessionHost }
        MainScope().launch { graph.appHost.root.collect { root -> if (root is RootState.SignedOut) session = null } }
        return graph.appHost
    }
}

/** Entry point for `:iosShell`: the app's presenter host, started the first time it is asked for. */
@Suppress("FunctionName") fun IosAppHost(fitDecoder: FitDecoder): AppHost = IosHost.start(appGraph(fitDecoder))

/**
 * Entry point for `:iosShell`: the bytes of the photo a `heldphoto:` URL names, from the signed-in session's uploads; null when signed out
 * or when the session doesn't hold it.
 */
@Suppress("FunctionName")
fun HeldPhotoBytes(url: String): ByteString? =
    if (HeldPhotos.isHeld(url)) IosHost.session?.feedRepository?.heldPhotos?.get(photoCid(url))?.bytes?.let(::ByteString) else null
