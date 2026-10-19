package io.github.solcott.activitytracker.app.di

import com.slack.circuit.subcircuit.SubCircuit
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.LegacyGroupMigration
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileSetup
import io.github.solcott.activitytracker.data.units.UnitsRepository

/** Everything that requires a signed-in account. Created per sign-in, dropped on sign-out. */
@GraphExtension(SessionScope::class)
interface SessionGraph {
    val sessionCircuit: SessionCircuit
    val subCircuit: SubCircuit
    val activityRepository: ActivityRepository
    val legacyGroupMigration: LegacyGroupMigration
    val feedRepository: FeedRepository
    val unitsRepository: UnitsRepository
    val profileSetup: ProfileSetup

    @GraphExtension.Factory
    interface Factory {
        fun createSessionGraph(@Provides session: OAuthSession): SessionGraph
    }
}
