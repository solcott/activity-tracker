package io.github.solcott.activitytracker.data.importsave

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.activityfile.ImportSource

/** A browser can't reopen a picked file in a later visit, so the web keeps none: a save's sources are `none` (feed/batch-import). */
@ContributesBinding(AppScope::class)
@Inject
class NoSourceKeeper : SourceKeeper {
    override suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?> = sources.map { null }

    override suspend fun open(kept: KeptSource): ReopenedSource? = null

    override suspend fun release(saveId: String, kept: List<KeptSource>) = Unit

    override suspend fun sweep(live: Map<String, List<KeptSource>>) = Unit
}
