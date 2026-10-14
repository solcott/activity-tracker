package io.github.solcott.activitytracker.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** For tests. The desktop app uses the file database from `DatabaseBindings`. */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>().addAppMigrations().setDriver(BundledSQLiteDriver()).build()
