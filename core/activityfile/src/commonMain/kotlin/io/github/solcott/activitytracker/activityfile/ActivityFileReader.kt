package io.github.solcott.activitytracker.activityfile

import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.lexicon.app.SourceFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads any activity file we support into [ImportedActivity]s: FIT, GPX, TCX, a Shearwater dive log (XML), or a UDDF or Subsurface logbook,
 * each optionally gzipped. The format comes from the content, not the file name, since exports and share sheets rename files freely. Runs
 * on [Dispatchers.Default].
 */
@Inject
class ActivityFileReader(private val fitDecoder: FitDecoder) {
    /**
     * One activity per file, except a logbook (UDDF, Subsurface), which gives one per dive in the file's order. Throws
     * [UnsupportedActivityFileException] for other files and [ActivityFileException] for unreadable ones.
     */
    suspend fun read(bytes: ByteArray): List<ImportedActivity> = readFile(bytes).activities

    /** Like [read], and also says which format the file is in and whether it came gzipped. */
    @Suppress("InjectDispatcher") // CPU-bound parsing; a constructor param would break the `ActivityFileReader { … }` SAM form tests use.
    suspend fun readFile(bytes: ByteArray): ImportedFile =
        withContext(Dispatchers.Default) {
            val gzipped = bytes.isGzip()
            val plain = if (gzipped) gunzip(bytes, MAX_UNZIPPED_BYTES) else bytes
            val (format, decoded) = decode(plain)
            ImportedFile(format, gzipped, decoded.map(DecodedActivityMapper::map))
        }

    private fun decode(bytes: ByteArray): Pair<ActivityFileFormat, List<DecodedActivity>> {
        if (bytes.isFit()) return ActivityFileFormat.FIT to listOf(fitDecoder.decode(bytes))
        val xml = decodeText(bytes)
        return when (xmlRootElement(xml)) {
            "gpx" -> ActivityFileFormat.GPX to listOf(GpxParser.parse(xml))
            "TrainingCenterDatabase" -> ActivityFileFormat.TCX to listOf(TcxParser.parse(xml))
            "dive" -> ActivityFileFormat.SHEARWATER to listOf(ShearwaterXmlParser.parse(xml))
            "uddf" -> ActivityFileFormat.UDDF to UddfParser.parse(xml)
            "divelog" -> ActivityFileFormat.SUBSURFACE to SubsurfaceXmlParser.parse(xml)
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

        /** FIT files carry ".FIT" at bytes 8 to 11 of their header. */
        private val FIT_SIGNATURE = ".FIT".encodeToByteArray()
        private const val FIT_SIGNATURE_OFFSET = 8

        fun ByteArray.isFit(): Boolean =
            size >= FIT_SIGNATURE_OFFSET + FIT_SIGNATURE.size &&
                FIT_SIGNATURE.indices.all { this[FIT_SIGNATURE_OFFSET + it] == FIT_SIGNATURE[it] }
    }
}

/** Far beyond any real activity (a 24-hour, 1 Hz GPX is about 20 MB); stops a gzip bomb. */
internal const val MAX_UNZIPPED_BYTES = 100 * 1024 * 1024

/** What [ActivityFileReader.readFile] read: the file's [format], whether it came [gzipped], and its [activities]. */
class ImportedFile(val format: ActivityFileFormat, val gzipped: Boolean, val activities: List<ImportedActivity>)

/** The formats [ActivityFileReader] reads, each with its `feed.activitySource#format` [token]. */
enum class ActivityFileFormat(val token: String) {
    FIT(SourceFormat.FIT),
    GPX(SourceFormat.GPX),
    TCX(SourceFormat.TCX),
    SHEARWATER(SourceFormat.SHEARWATER),
    UDDF(SourceFormat.UDDF),
    SUBSURFACE(SourceFormat.SUBSURFACE);

    /** Whether one file can hold several dives, each imported as its own activity. */
    val isLogbook: Boolean
        get() = this == UDDF || this == SUBSURFACE

    companion object {
        /** The format with [token], or null for one this app doesn't read. */
        fun of(token: String): ActivityFileFormat? = entries.firstOrNull { it.token == token }
    }
}
