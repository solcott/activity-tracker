package io.github.solcott.activitytracker.app.di

import androidx.compose.runtime.Stable
import com.slack.circuit.subcircuit.SubCircuit
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.solcott.activitytracker.app.host.SessionHost
import io.github.solcott.activitytracker.app.host.SessionRunner
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.activity.OutboxPacer
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.export.DataExporter
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.importsave.ImportSaveRunner
import io.github.solcott.activitytracker.data.importsave.ImportSaveStore
import io.github.solcott.activitytracker.data.units.UnitsRepository

/**
 * Everything that requires a signed-in account. Created per sign-in, dropped on sign-out. `@Stable` because `App`'s pieces take it as a
 * param: one instance per session, and its bindings never change.
 */
@Stable
@GraphExtension(SessionScope::class)
interface SessionGraph {
    val sessionCircuit: SessionCircuit

    /** What the session shows and its background work, shared with the presenter host (dev/presenter-host). */
    val sessionRunner: SessionRunner

    /** This account's screens run with no UI (dev/presenter-host). Unused by the Compose shell. */
    val sessionHost: SessionHost
    val subCircuit: SubCircuit
    val outboxPacer: OutboxPacer
    val uploadQueue: UploadQueue
    val feedRepository: FeedRepository
    val unitsRepository: UnitsRepository
    val importSaveStore: ImportSaveStore
    val importSaveRunner: ImportSaveRunner
    val dataExporter: DataExporter

    @GraphExtension.Factory
    interface Factory {
        fun createSessionGraph(@Provides session: OAuthSession): SessionGraph
    }
}
