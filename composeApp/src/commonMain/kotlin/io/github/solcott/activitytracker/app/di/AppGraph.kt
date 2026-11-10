package io.github.solcott.activitytracker.app.di

import androidx.compose.runtime.Stable
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.SessionHostFactory
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.location.ActivityRecorder

/**
 * Common view of the app graph. Each platform declares the real `@DependencyGraph(AppScope::class)` (`AndroidAppGraph`, `IosAppGraph`,
 * `WebAppGraph`, `DesktopAppGraph`) extending this, so contributions from platform source sets (database, location, auth browser) are
 * visible to it.
 *
 * `@Stable` because `App` takes it as a param: there's one instance for the app's lifetime and its bindings never change.
 */
@Stable
interface AppGraph : SessionGraph.Factory {
    val appCircuit: AppCircuit

    /** The app's screens run with no UI, for a shell that draws them natively (dev/presenter-host). Unused by the Compose shell. */
    val appHost: AppHost
    val authRepository: AuthRepository
    val authCoordinator: AuthCoordinator
    val activityRecorder: ActivityRecorder
    val importSaves: ImportSaves
    val dataExports: DataExports
    val recordScreenRequests: RecordScreenRequests
    val accountScreenRequests: AccountScreenRequests
}

/** Makes each signed-in account's host from its session graph, which only this module can name. */
fun AppGraph.sessionHosts(): SessionHostFactory = SessionHostFactory { session -> createSessionGraph(session).sessionHost }
