package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.PlatformFile

/**
 * [this] picked file as the import reads it, a range at a time, so an archive of several gigabytes is never held whole: FileKit's
 * `source()` off the web, the browser's `Blob.slice` on it.
 */
internal expect fun PlatformFile.toImportSource(): ImportSource
