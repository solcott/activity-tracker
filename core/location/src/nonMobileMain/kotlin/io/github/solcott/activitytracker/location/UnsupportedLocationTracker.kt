package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop and web record no GPS activities. The record presenter is still aggregated into their graphs, so this satisfies the binding, and
 * [isSupported] tells the feed to hide the Record action.
 */
@ContributesBinding(AppScope::class)
@Inject
class UnsupportedLocationTracker : LocationTracker {
    override val isSupported: Boolean = false

    override fun hasPermission(): Boolean = false

    override fun updates(): Flow<LocationSample> = emptyFlow()
}
