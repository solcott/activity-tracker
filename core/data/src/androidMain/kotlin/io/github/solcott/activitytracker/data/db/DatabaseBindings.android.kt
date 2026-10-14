package io.github.solcott.activitytracker.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun database(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(DATABASE_NAME).absolutePath)
            .addAppMigrations()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .build()
}
