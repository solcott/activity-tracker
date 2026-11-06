package io.github.solcott.activitytracker.data.export

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * [ExportFiles] in a folder of the device's file system ([root], app-private and kept out of backups by each platform's binding), with
 * [free] saying how much room is left there. Each write goes to a `.part` file first and is moved into place, so a file is never left half
 * written under its own name.
 */
class FileSystemExportFiles(private val root: Path, private val io: CoroutineDispatcher, private val free: () -> Long?) : ExportFiles {
    override suspend fun write(exportId: String, path: String, bytes: ByteArray) =
        withContext(io) {
            free()?.let { if (it < bytes.size + EXPORT_SPACE_MARGIN) throw NoSpaceException(bytes.size + EXPORT_SPACE_MARGIN - it) }
            val target = fileOf(exportId, path)
            target.parent?.let { SystemFileSystem.createDirectories(it) }
            val part = Path("$target.part")
            try {
                SystemFileSystem.sink(part).buffered().use { it.write(bytes) }
                SystemFileSystem.atomicMove(part, target)
            } catch (e: IOException) {
                SystemFileSystem.delete(part, mustExist = false)
                // A full disk can still surprise the check above (another app wrote meanwhile).
                if (free()?.let { it < bytes.size } == true) throw NoSpaceException(bytes.size.toLong(), e)
                throw e
            }
        }

    override suspend fun read(exportId: String, path: String): ByteArray? =
        withContext(io) {
            val file = fileOf(exportId, path)
            if (!SystemFileSystem.exists(file)) null else SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        }

    override suspend fun list(exportId: String): List<String> =
        withContext(io) {
            val dir = Path(root, exportId)
            if (!SystemFileSystem.exists(dir)) emptyList() else buildList { walk(dir, "") }
        }

    private fun MutableList<String>.walk(dir: Path, prefix: String) {
        for (child in SystemFileSystem.list(dir)) {
            val name = child.name
            val relative = if (prefix.isEmpty()) name else "$prefix/$name"
            when {
                SystemFileSystem.metadataOrNull(child)?.isDirectory == true -> walk(child, relative)
                !name.endsWith(".part") -> add(relative)
            }
        }
    }

    override suspend fun delete(exportId: String) = withContext(io) { deleteTree(Path(root, exportId)) }

    private fun deleteTree(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) SystemFileSystem.list(path).forEach(::deleteTree)
        SystemFileSystem.delete(path, mustExist = false)
    }

    override suspend fun exportIds(): List<String> =
        withContext(io) { if (SystemFileSystem.exists(root)) SystemFileSystem.list(root).map { it.name } else emptyList() }

    override suspend fun freeBytes(): Long? = withContext(io) { free() }

    private fun fileOf(exportId: String, path: String): Path {
        require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Not a relative path: $path" }
        return path.split('/').fold(Path(root, exportId)) { dir, name -> Path(dir, name) }
    }
}
