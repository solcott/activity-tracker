@file:OptIn(ExperimentalForeignApi::class)

package io.github.solcott.activitytracker.data.export

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Data exports are staged in `Application Support/exports/`, which iOS never purges, marked excluded from backups: they hold private data
 * and are deleted once saved.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ExportFilesBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun exportFiles(): ExportFiles {
        val support = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        val root = checkNotNull(support.URLByAppendingPathComponent("exports"))
        NSFileManager.defaultManager.createDirectoryAtURL(root, withIntermediateDirectories = true, attributes = null, error = null)
        root.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        val path = checkNotNull(root.path)
        return FileSystemExportFiles(Path(path), Dispatchers.IO) {
            (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, error = null)?.get(NSFileSystemFreeSize) as? NSNumber)
                ?.longLongValue
        }
    }
}
