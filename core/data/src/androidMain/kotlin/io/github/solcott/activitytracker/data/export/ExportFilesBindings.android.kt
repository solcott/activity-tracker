package io.github.solcott.activitytracker.data.export

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path

/** Data exports are staged in `noBackupFilesDir/exports/`, which backups and device transfers never carry: they hold private data. */
@ContributesTo(AppScope::class)
@BindingContainer
object ExportFilesBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun exportFiles(context: Context, io: CoroutineDispatcher = Dispatchers.IO): ExportFiles {
        val root = File(context.noBackupFilesDir, "exports")
        return FileSystemExportFiles(Path(root.path), io) { context.noBackupFilesDir.usableSpace }
    }
}
