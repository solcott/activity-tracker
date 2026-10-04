package io.github.solcott.activitytracker.app.di

import androidx.compose.runtime.Stable
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthRepository

/**
 * Common view of the app graph. Each platform declares the real `@DependencyGraph(AppScope::class)` (`AndroidAppGraph`, `IosAppGraph`,
 * `WebAppGraph`) extending this, so contributions from platform source sets (database, location, auth browser) are visible to it.
 *
 * `@Stable` because `App` takes it as a param: there's one instance for the app's lifetime and its bindings never change.
 */
@Stable
interface AppGraph : SessionGraph.Factory {
    val appCircuit: AppCircuit
    val authRepository: AuthRepository
    val authCoordinator: AuthCoordinator
}
