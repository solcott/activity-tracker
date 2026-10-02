package io.github.solcott.activitytracker.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** JVM is only used for tests: an in-memory database. */
fun inMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
