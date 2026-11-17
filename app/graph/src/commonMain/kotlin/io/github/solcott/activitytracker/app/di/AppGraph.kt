package io.github.solcott.activitytracker.app.di

import androidx.compose.runtime.Stable
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.SessionHost
import io.github.solcott.activitytracker.app.host.SessionHostFactory
import io.github.solcott.activitytracker.app.links.HandleResolution
import io.github.solcott.activitytracker.app.links.LinkRequests
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.location.ActivityRecorder

/**
 * Common view of the app graph. Each platform declares the real `@DependencyGraph(AppScope::class)` (`AndroidAppGraph`, `IosAppGraph`,
 * `WebAppGraph`, `DesktopAppGraph`) extending this, so contributions from platform source sets (database, location, auth browser) are
 * visible to it. It names no UI: the Compose apps reach their Circuit through what `:composeApp` contributes (`AppCircuits`), which the iOS
 * graph never sees.
 *
 * `@Stable` because `App` takes it as a param: there's one instance for the app's lifetime and its bindings never change.
 */
@Stable
interface AppGraph : SessionGraph.Factory {
    /** The app's screens run with no UI, for a shell that draws them natively (dev/presenter-host). Unused by the Compose shell. */
    val appHost: AppHost
    val authRepository: AuthRepository
    val authCoordinator: AuthCoordinator
    val activityRecorder: ActivityRecorder
    val importSaves: ImportSaves
    val dataExports: DataExports

    /** The links the app was asked to open from outside its UI, and the notifications' requests (ui/deep-links). */
    val linkRequests: LinkRequests

    /** Resolves a handle in a link to its DID, for the Compose shell's link opener (the host has its own). */
    val handleResolution: HandleResolution
}

/**
 * Makes each signed-in account's host from its session graph, which only this module can name, and closes the graph when the host is
 * released. [onSession] hears the graph while its host is shown, and null once it's released. [AppHost] shows one host at a time.
 */
fun AppGraph.sessionHosts(onSession: (SessionGraph?) -> Unit = {}): SessionHostFactory =
    object : SessionHostFactory {
        private var current: SessionGraph? = null

        override fun create(session: OAuthSession): SessionHost =
            createSessionGraph(session)
                .also {
                    current = it
                    onSession(it)
                }
                .sessionHost

        override fun release(host: SessionHost) {
            val graph = current?.takeIf { it.sessionHost === host } ?: return
            current = null
            onSession(null)
            graph.close()
        }
    }
