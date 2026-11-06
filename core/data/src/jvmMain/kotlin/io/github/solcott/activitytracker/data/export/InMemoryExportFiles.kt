package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.data.db.AppDatabase
import kotlin.time.Clock

/** [ExportFiles] in memory, with [free] bytes left (null: unknown) and every write counted, for tests. A plain class: never contributed. */
class InMemoryExportFiles(var free: Long? = null) : ExportFiles {
    val files = mutableMapOf<String, MutableMap<String, ByteArray>>()
    var writes = 0

    override suspend fun write(exportId: String, path: String, bytes: ByteArray) {
        free?.let { if (it < bytes.size + EXPORT_SPACE_MARGIN) throw NoSpaceException(bytes.size + EXPORT_SPACE_MARGIN - it) }
        files.getOrPut(exportId) { mutableMapOf() }[path] = bytes
        writes++
    }

    override suspend fun read(exportId: String, path: String): ByteArray? = files[exportId]?.get(path)

    override suspend fun list(exportId: String): List<String> = files[exportId]?.keys?.toList().orEmpty()

    override suspend fun delete(exportId: String) {
        files.remove(exportId)
    }

    override suspend fun exportIds(): List<String> = files.keys.toList()

    override suspend fun freeBytes(): Long? = free

    fun text(exportId: String, path: String): String? = files[exportId]?.get(path)?.decodeToString()
}

/** Data exports app-wide over [db], staging in memory, for tests. */
fun testDataExports(db: AppDatabase, files: ExportFiles = InMemoryExportFiles()): DataExports = DataExports(db, files, Clock.System)
