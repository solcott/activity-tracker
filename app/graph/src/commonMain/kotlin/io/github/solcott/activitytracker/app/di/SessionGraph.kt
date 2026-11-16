package io.github.solcott.activitytracker.app.di

import androidx.compose.runtime.Stable
import com.slack.circuit.subcircuit.SubPresenterFactory
import dev.zacsweers.metro.CloseableGraph
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ExperimentalMetroApi
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
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
 * Everything that requires a signed-in account. Created per sign-in, and closed when it's dropped: on sign-out, on another account's
 * sign-in, and when the background passes move to another account. Closing runs the `@OnGraphClose` callbacks contributed to
 * [SessionScope], which stop the session's own work (a running leave), and leaves the graph still serving, so drop it first. `@Stable`
 * because `App`'s pieces take it as a param: one instance per session, and its bindings never change. Like [AppGraph] it names no UI: the
 * Compose apps' session Circuit and sub-screens are `:composeApp`'s `SessionCircuits`.
 */
@Stable
@OptIn(ExperimentalMetroApi::class) // Graph lifecycle callbacks (Metro 1.6).
@GraphExtension(SessionScope::class)
interface SessionGraph : CloseableGraph {
    /** What the session shows and its background work, shared with the presenter host (dev/presenter-host). */
    val sessionRunner: SessionRunner

    /** This account's screens run with no UI (dev/presenter-host). Unused by the Compose shell. */
    val sessionHost: SessionHost
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

/**
 * Declares the sub-screens' presenter factories for every shell, so the graph is valid before (and without) any `@SubCircuitInject`
 * contribution: the presenter host runs sub-screens on iOS (dev/presenter-host), and the Compose apps' `SubCircuit` takes them too.
 */
@ContributesTo(SessionScope::class)
interface SubPresenterMultibinds {
    @Multibinds(allowEmpty = true) fun subPresenterFactories(): Set<SubPresenterFactory>
}
