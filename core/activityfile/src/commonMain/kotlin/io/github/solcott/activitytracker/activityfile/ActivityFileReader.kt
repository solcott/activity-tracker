package io.github.solcott.activitytracker.activityfile

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads any activity file we support into an [ImportedActivity]: FIT, GPX or TCX, each optionally gzipped. The format comes from the
 * content, not the file name, since exports and share sheets rename files freely. Runs on [Dispatchers.Default].
 */
@Inject
class ActivityFileReader(private val fitDecoder: FitDecoder) {
    /** Throws [UnsupportedActivityFileException] for other files and [ActivityFileException] for unreadable ones. */
    suspend fun read(bytes: ByteArray): ImportedActivity =
        withContext(Dispatchers.Default) {
            val plain = if (bytes.isGzip()) gunzip(bytes, MAX_UNZIPPED_BYTES) else bytes
            DecodedActivityMapper.map(decode(plain))
        }

    private fun decode(bytes: ByteArray): DecodedActivity {
        if (bytes.isFit()) return fitDecoder.decode(bytes)
        // Some exporters (Garmin's TCX among them) start with a UTF-8 byte-order mark, which isn't part of the XML.
        val xml = bytes.decodeToString().removePrefix("\uFEFF")
        return when (xmlRootElement(xml)) {
            "gpx" -> GpxParser.parse(xml)
            "TrainingCenterDatabase" -> TcxParser.parse(xml)
            else -> throw UnsupportedActivityFileException()
        }
    }

    private companion object {
        /** Far beyond any real activity (a 24-hour, 1 Hz GPX is about 20 MB); stops a gzip bomb. */
        const val MAX_UNZIPPED_BYTES = 100 * 1024 * 1024

        /** FIT files carry ".FIT" at bytes 8 to 11 of their header. */
        private val FIT_SIGNATURE = ".FIT".encodeToByteArray()
        private const val FIT_SIGNATURE_OFFSET = 8

        fun ByteArray.isFit(): Boolean =
            size >= FIT_SIGNATURE_OFFSET + FIT_SIGNATURE.size &&
                FIT_SIGNATURE.indices.all { this[FIT_SIGNATURE_OFFSET + it] == FIT_SIGNATURE[it] }
    }
}
