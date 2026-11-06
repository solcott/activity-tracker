package io.github.solcott.activitytracker.data.export

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.DesktopAppDirs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path

/** Data exports are staged in the app's data folder, beside its database. */
@ContributesTo(AppScope::class)
@BindingContainer
object ExportFilesBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun exportFiles(dirs: DesktopAppDirs, io: CoroutineDispatcher = Dispatchers.IO): ExportFiles =
        FileSystemExportFiles(Path(dirs.data.resolve("exports").toString()), io) { dirs.data.toFile().usableSpace }
}
