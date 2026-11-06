package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.activityfile.ImportSource

/**
 * A [SourceKeeper] that keeps nothing: a save reads the files picked in this process, and none can be reopened later. The web's binding
 * ([NoSourceKeeper], since a browser can't reopen a picked file in a later visit) and tests use it.
 */
object KeepNothing : SourceKeeper {
    override suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?> = sources.map { null }

    override suspend fun open(kept: KeptSource): ReopenedSource? = null

    override suspend fun release(saveId: String, kept: List<KeptSource>) = Unit

    override suspend fun sweep(live: Map<String, List<KeptSource>>) = Unit
}
