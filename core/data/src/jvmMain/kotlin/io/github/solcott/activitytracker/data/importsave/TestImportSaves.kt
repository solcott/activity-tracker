package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.data.db.AppDatabase

/** Batch saves app-wide over [db] for a test, keeping no files ([KeepNothing]). */
fun testImportSaves(db: AppDatabase): ImportSaves = ImportSaves(db, KeepNothing)
