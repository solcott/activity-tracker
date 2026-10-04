package io.github.solcott.activitytracker.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Scripted samples for tests. The desktop app binds [UnsupportedLocationTracker] instead. */
class FakeLocationTracker(private val samples: List<LocationSample> = emptyList()) : LocationTracker {
    override fun hasPermission(): Boolean = true

    override fun updates(): Flow<LocationSample> = samples.asFlow()
}
