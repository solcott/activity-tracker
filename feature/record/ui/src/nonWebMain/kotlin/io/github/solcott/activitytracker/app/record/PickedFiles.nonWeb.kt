package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered

internal actual fun PlatformFile.toImportSource(): ImportSource = PickedFileSource(this)

/**
 * A picked file read through one open stream while reads go forward, as an import's mostly do; a read elsewhere reopens it there. Closed
 * once a read reaches its end.
 */
private class PickedFileSource(private val file: PlatformFile, private val io: CoroutineDispatcher = Dispatchers.IO) : ImportSource {
    private val mutex = Mutex()
    private var open: Source? = null
    private var position = 0L

    override val name: String = file.name
    override val size: Long by lazy { file.size() }

    override suspend fun read(offset: Long, length: Int): ByteArray = mutex.withLock {
        withContext(io) {
            try {
                val source = open?.takeIf { position == offset } ?: reopenAt(offset)
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = source.readAtMostTo(bytes, read, length)
                    if (count < 0) break
                    read += count
                }
                position += read
                if (read < length) close()
                bytes.copyOf(read)
            } catch (e: kotlinx.io.IOException) {
                close()
                throw ActivityFileException("Couldn't read $name", e)
            }
        }
    }

    private fun reopenAt(offset: Long): Source {
        close()
        val source = file.source().buffered()
        source.skip(offset)
        open = source
        position = offset
        return source
    }

    private fun close() {
        open?.close()
        open = null
    }
}
