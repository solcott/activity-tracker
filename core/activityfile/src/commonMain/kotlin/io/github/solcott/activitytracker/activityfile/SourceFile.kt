package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivitySourceRecord
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An imported file as it's kept (`feed.activitySource`): [stored] is always gzip, either the file as picked when it came gzipped
 * ([compressed] false) or the app's gzip of it ([compressed] true). [size] and [fileName] are the file's as picked. Every activity read
 * from one file shares one [SourceFile], whose [key] names its one stored copy in the outbox.
 */
class SourceFile(
    val stored: ByteArray,
    val compressed: Boolean,
    val format: ActivityFileFormat,
    val fileName: String,
    val size: Long,
    val key: String,
) {
    companion object {
        /** The lexicon's `maxSize` for the stored file. */
        const val MAX_STORED_BYTES = ActivitySourceRecord.MAX_FILE_BYTES

        private const val KEY_BYTES = 16

        /**
         * Prepares the picked [bytes], which [ActivityFileReader.readFile] read as [file], to be kept: gzips them unless they came gzipped.
         * Null when the stored form is over [MAX_STORED_BYTES], so the activities import without it. Runs on [Dispatchers.Default].
         */
        @Suppress("InjectDispatcher") // CPU-bound compression, like ActivityFileReader.
        suspend fun prepare(bytes: ByteArray, fileName: String, file: ImportedFile): SourceFile? =
            withContext(Dispatchers.Default) {
                val stored = if (file.gzipped) bytes else gzip(bytes)
                if (stored.size > MAX_STORED_BYTES) return@withContext null
                SourceFile(
                    stored = stored,
                    compressed = !file.gzipped,
                    format = file.format,
                    fileName = fileName,
                    size = bytes.size.toLong(),
                    key = Random.nextBytes(KEY_BYTES).toHexString(),
                )
            }

        /** The file as picked, from what [prepare] stored. Throws [ActivityFileException] for a corrupt stored file. */
        suspend fun original(stored: ByteArray, compressed: Boolean): ByteArray =
            if (compressed) gunzip(stored, MAX_UNZIPPED_BYTES) else stored
    }
}
