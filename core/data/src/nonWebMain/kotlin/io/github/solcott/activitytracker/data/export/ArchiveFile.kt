package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.sink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered

/** Zips this archive into [target] (a file the athlete picked, or one to share) as it goes, replacing what it held. Throws on failure. */
suspend fun ExportArchive.writeTo(target: PlatformFile, io: CoroutineDispatcher = Dispatchers.IO) =
    withContext(io) { target.sink().buffered().use { sink -> writeTo { sink.write(it) } } }

/**
 * Zips this archive into the file at [path], replacing one already there, for a UI that hands the file on itself (the native iOS share
 * sheet). Whether it was written: on failure the partial file is deleted, so nothing half-written is offered.
 */
suspend fun ExportArchive.writeToPath(path: String, io: CoroutineDispatcher = Dispatchers.IO): Boolean {
    val target = PlatformFile(path)
    return runSuspendCatching {
        if (target.exists()) target.delete()
        writeTo(target, io)
    }
        .onFailure { runSuspendCatching { if (target.exists()) target.delete() } }
        .isSuccess
}

/**
 * Writes an activity's exported [file] to the file at [path], replacing one already there, for the native iOS activity page to offer in the
 * share sheet (feed/activity-export). Whether it was written; on failure nothing is left.
 */
suspend fun ExportedFile.writeToPath(path: String, io: CoroutineDispatcher = Dispatchers.IO): Boolean {
    val target = PlatformFile(path)
    return runSuspendCatching {
        withContext(io) {
            if (target.exists()) target.delete()
            target.sink().buffered().use { it.write(bytes) }
        }
    }
        .onFailure { runSuspendCatching { if (target.exists()) target.delete() } }
        .isSuccess
}
