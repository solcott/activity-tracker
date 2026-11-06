package io.github.solcott.activitytracker.data.export

/**
 * App-private storage for data exports' staged files (auth/data-export): one folder per export, never backed up or shared, deleted with the
 * export. Paths are relative to the export's folder and `/`-separated. Writes replace a file whole, so a resumed export can rewrite one it
 * may have been writing when the process died.
 */
interface ExportFiles {
    /** Writes [bytes] as [path] of export [exportId]. Throws [NoSpaceException] when the device hasn't room for them. */
    suspend fun write(exportId: String, path: String, bytes: ByteArray)

    /** [path] of export [exportId], or null when it was never written. */
    suspend fun read(exportId: String, path: String): ByteArray?

    /** Every file export [exportId] has staged, as relative paths, in no particular order. */
    suspend fun list(exportId: String): List<String>

    /** Deletes export [exportId]'s folder and everything in it; nothing there counts as done. */
    suspend fun delete(exportId: String)

    /** The exports that have a folder, so one whose row is gone can be swept. */
    suspend fun exportIds(): List<String>

    /** Free bytes where exports are kept, or null when the platform won't say. */
    suspend fun freeBytes(): Long?
}

/** The device hasn't room for a staged file: the export pauses until the athlete frees some. [needed] is how many bytes, when known. */
class NoSpaceException(val needed: Long? = null, cause: Throwable? = null) : Exception("Not enough free space for the export", cause)

/** Bytes kept free beyond what a write needs, so an export never fills the device to the last byte. */
internal const val EXPORT_SPACE_MARGIN = 50L * 1024 * 1024
