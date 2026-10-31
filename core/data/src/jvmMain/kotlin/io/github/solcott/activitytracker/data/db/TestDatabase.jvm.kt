package io.github.solcott.activitytracker.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext

/**
 * For tests. The desktop app uses the file database from `DatabaseBindings`. With [queryContext], queries and flows run there (a test
 * dispatcher, so a test in virtual time never waits on another thread).
 */
fun inMemoryDatabase(queryContext: CoroutineContext? = null): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>()
        .addAppMigrations()
        .setDriver(BundledSQLiteDriver())
        .apply { if (queryContext != null) setQueryCoroutineContext(queryContext) }
        .build()
