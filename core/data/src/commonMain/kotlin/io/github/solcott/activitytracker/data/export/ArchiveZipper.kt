package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlinx.datetime.LocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import no.synth.kmpzip.crypto.Crc32
import no.synth.kmpzip.kotlinx.ZipOutputStream
import no.synth.kmpzip.zip.ZipConstants
import no.synth.kmpzip.zip.ZipEntry

/**
 * Turns an export's staged files into its zip (auth/data-export), streamed: each entry is written through a small buffer that's handed to
 * [write] chunk by chunk, so the archive is never whole in memory and the caller can write each chunk where it goes (a file the athlete
 * picked, or OPFS on the web, whose writes are asynchronous). Entries that are already compressed (gzip, images) are stored; the rest are
 * deflated. ZIP64 records are written when the archive needs them. Original files' blobs are left out: they're in `activities/`.
 */
class ArchiveZipper(private val files: ExportFiles) {
    /** Writes export [id]'s archive, its entries dated [at] (zip times are local, with no zone), to [write]. */
    suspend fun zip(id: String, at: LocalDateTime, write: suspend (ByteArray) -> Unit) {
        val time = dosTime(at)
        val buffer = Buffer()
        val zip = ZipOutputStream(buffer)
        suspend fun drain() {
            if (buffer.size > 0) write(buffer.readByteArray())
        }
        for (path in entries(id)) {
            val bytes = files.read(id, path) ?: continue
            val entry = ZipEntry(path)
            entry.time = time
            if (isCompressed(bytes)) {
                entry.method = ZipConstants.STORED
                entry.size = bytes.size.toLong()
                entry.crc = Crc32().apply { update(bytes, 0, bytes.size) }.value()
            } else {
                entry.method = ZipConstants.DEFLATED
            }
            zip.putNextEntry(entry)
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(CHUNK, bytes.size - offset)
                zip.write(bytes, offset, length)
                offset += length
                drain()
            }
            zip.closeEntry()
            drain()
        }
        zip.finish()
        zip.close()
        drain()
    }

    /** The staged files that go in the archive, in archive order: README and manifest first, then activities, records and blobs. */
    internal suspend fun entries(id: String): List<String> {
        val staged = files.list(id).toSet()
        val originals =
            files
                .read(id, DataExporter.MANIFEST)
                ?.let { Json.decodeFromString(ExportManifest.serializer(), it.decodeToString()) }
                ?.originals
                ?.keys
                .orEmpty()
        val skipped = originals.map { DataExporter.blobPath(AthleteSpace.SELF, it) }.toSet()
        return listOf(DataExporter.README, DataExporter.MANIFEST).filter { it in staged } +
            staged.filter { it.startsWith("${DataExporter.ACTIVITIES}/") }.sorted() +
            staged.filter { it.startsWith("records/") }.sorted() +
            staged.filter { it.startsWith("blobs/") && it !in skipped }.sorted()
    }

    private fun isCompressed(bytes: ByteArray): Boolean = COMPRESSED_MAGIC.any { magic ->
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }
    }

    private companion object {
        const val CHUNK = 1024 * 1024

        /** gzip, PNG, JPEG, WebP (RIFF), zip. */
        val COMPRESSED_MAGIC =
            listOf(
                byteArrayOf(0x1f, 0x8b.toByte()),
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
                byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()),
                byteArrayOf(0x52, 0x49, 0x46, 0x46),
                byteArrayOf(0x50, 0x4b, 0x03, 0x04),
            )
    }
}

/**
 * [at] as an MS-DOS date and time, what [ZipEntry.time] holds (not epoch milliseconds): years since 1980, month, day, hour, minute and
 * seconds halved, packed into 32 bits. Clamped to 1980, the earliest it can say.
 */
internal fun dosTime(at: LocalDateTime): Long {
    if (at.year < DOS_EPOCH_YEAR) return DOS_EPOCH
    val date = ((at.year - DOS_EPOCH_YEAR) shl YEAR_SHIFT) or (at.month.ordinal + 1 shl MONTH_SHIFT) or at.day
    val time = (at.hour shl HOUR_SHIFT) or (at.minute shl MINUTE_SHIFT) or (at.second / 2)
    return (date.toLong() shl DATE_SHIFT) or time.toLong()
}

private const val DOS_EPOCH_YEAR = 1980
private const val YEAR_SHIFT = 9
private const val MONTH_SHIFT = 5
private const val HOUR_SHIFT = 11
private const val MINUTE_SHIFT = 5
private const val DATE_SHIFT = 16

/** 1980-01-01 00:00. */
private const val DOS_EPOCH = 0x210000L
