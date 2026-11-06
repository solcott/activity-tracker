package io.github.solcott.activitytracker.data.export

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered

/** Zips this archive into [target] (a file the athlete picked, or one to share) as it goes, replacing what it held. Throws on failure. */
suspend fun ExportArchive.writeTo(target: PlatformFile, io: CoroutineDispatcher = Dispatchers.IO) =
    withContext(io) { target.sink().buffered().use { sink -> writeTo { sink.write(it) } } }
