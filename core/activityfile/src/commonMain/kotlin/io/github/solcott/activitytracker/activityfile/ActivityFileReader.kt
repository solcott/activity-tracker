package io.github.solcott.activitytracker.activityfile

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads any activity file we support into an [ImportedActivity]: FIT, GPX, TCX or a Shearwater dive log (XML), each optionally gzipped. The
 * format comes from the content, not the file name, since exports and share sheets rename files freely. Runs on [Dispatchers.Default].
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
        val xml = decodeText(bytes)
        return when (xmlRootElement(xml)) {
            "gpx" -> GpxParser.parse(xml)
            "TrainingCenterDatabase" -> TcxParser.parse(xml)
            "dive" -> ShearwaterXmlParser.parse(xml)
            else -> throw UnsupportedActivityFileException()
        }
    }

    private companion object {
        /**
         * UTF-8, or UTF-16 when the file starts with its byte-order mark. The XML prolog's `encoding` isn't trusted: Shearwater Cloud
         * declares "utf-16" on plain ASCII. Some exporters (Garmin's TCX among them) start with a UTF-8 byte-order mark, which isn't part
         * of the XML.
         */
        fun decodeText(bytes: ByteArray): String {
            val bigEndian =
                when {
                    bytes.startsWith(0xFE, 0xFF) -> true
                    bytes.startsWith(0xFF, 0xFE) -> false
                    else -> return bytes.decodeToString().removePrefix("\uFEFF")
                }
            val chars =
                CharArray((bytes.size - 2) / 2) { i ->
                    val high = bytes[2 + 2 * i + (if (bigEndian) 0 else 1)].toInt() and 0xFF
                    val low = bytes[2 + 2 * i + (if (bigEndian) 1 else 0)].toInt() and 0xFF
                    ((high shl 8) or low).toChar()
                }
            return chars.concatToString()
        }

        private fun ByteArray.startsWith(first: Int, second: Int) = size >= 2 && this[0] == first.toByte() && this[1] == second.toByte()

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
