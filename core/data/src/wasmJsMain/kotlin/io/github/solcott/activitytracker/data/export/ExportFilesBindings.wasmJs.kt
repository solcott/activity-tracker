package io.github.solcott.activitytracker.data.export

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Data exports are staged in the origin private file system. */
@ContributesTo(AppScope::class)
@BindingContainer
object ExportFilesBindings {
    @Provides @SingleIn(AppScope::class) fun exportFiles(): ExportFiles = OpfsExportFiles()
}
