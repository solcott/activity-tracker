package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.data.db.AppDatabase

/** A [SourceKeeper] for tests that keeps nothing, as on the web: a save reads the files picked in this process. */
object KeepNothing : SourceKeeper {
    override suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?> = sources.map { null }

    override suspend fun open(kept: KeptSource): ReopenedSource? = null

    override suspend fun release(saveId: String, kept: List<KeptSource>) = Unit

    override suspend fun sweep(live: Map<String, List<KeptSource>>) = Unit
}

/** Batch saves app-wide over [db] for a test, keeping no files ([KeepNothing]). */
fun testImportSaves(db: AppDatabase): ImportSaves = ImportSaves(db, KeepNothing)
