package io.github.solcott.activitytracker.data.db

import androidx.room3.Room
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.web.sqlite.createSqliteWorkerDriver

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {
    /** Persisted to the browser's Origin Private File System by the SQLite WASM worker. */
    @Provides
    @SingleIn(AppScope::class)
    fun database(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(DATABASE_NAME).addAppMigrations().setDriver(createSqliteWorkerDriver()).build()
}
