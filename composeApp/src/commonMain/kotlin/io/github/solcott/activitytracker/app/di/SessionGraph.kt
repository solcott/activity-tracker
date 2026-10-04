package io.github.solcott.activitytracker.app.di

import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitsRepository

/** Everything that requires a signed-in account. Created per sign-in, dropped on sign-out. */
@GraphExtension(SessionScope::class)
interface SessionGraph {
    val sessionCircuit: SessionCircuit
    val activityRepository: ActivityRepository
    val unitsRepository: UnitsRepository

    @GraphExtension.Factory
    interface Factory {
        fun createSessionGraph(@Provides session: OAuthSession): SessionGraph
    }
}
