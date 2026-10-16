package io.github.solcott.activitytracker.data.activity

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ExportActivity
import io.github.solcott.activitytracker.activityfile.ExportPoints
import io.github.solcott.activitytracker.activityfile.ExportTotals
import io.github.solcott.activitytracker.activityfile.FitWriter
import io.github.solcott.activitytracker.activityfile.GpxWriter
import io.github.solcott.activitytracker.activityfile.TcxSport
import io.github.solcott.activitytracker.activityfile.TcxWriter
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A file format an activity exports as. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    GPX("gpx", Gpx.MIME_TYPE),
    TCX("tcx", "application/vnd.garmin.tcx+xml"),
    FIT("fit", "application/vnd.ant.fit"),
}

/** An exported file waiting to be handed to the platform. Equal by name, type and the very same bytes, so it's cheap to compare. */
@Immutable
class ExportedFile(val name: String, val mimeType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is ExportedFile && other.name == name && other.mimeType == mimeType && other.bytes === bytes

    override fun hashCode(): Int = name.hashCode() * HASH_PRIME + mimeType.hashCode()

    override fun toString(): String = "ExportedFile($name, $mimeType, ${bytes.size} bytes)"

    private companion object {
        const val HASH_PRIME = 31
    }
}

/** The activity still has writes in the outbox, so a file built now would miss what's queued. */
class NotSentYetException : IllegalStateException("The activity can be exported once it's sent")

/** The track or streams couldn't be read, so the file would be thinner than what's stored. */
class ExportUnreadableException(cause: Throwable? = null) : IllegalStateException("Couldn't read the activity's track or streams", cause)

/**
 * Rebuilds the signed-in athlete's own activity as a GPX, TCX or FIT file from its record, its streams (from the AppView, as charts read
 * them) and its full track (from the `self` space, untrimmed by privacy zones: it's the athlete's own data).
 */
@SingleIn(SessionScope::class)
@Inject
class ActivityExporter(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val tracks: ActivityTrackStore,
    private val feed: FeedRepository,
) {
    /**
     * Builds [view] as [format], reading its track and streams fresh. Throws [NotSentYetException] while its record, streams or track wait
     * in the outbox, and [ExportUnreadableException] when either can't be read or [format] has nothing to write.
     */
    suspend fun export(view: ActivityView, format: ExportFormat): ExportedFile {
        requireSent(view)
        val track = readTrack(view)
        val streams = readStreams(view)
        if (format !in formats(track, streams != null)) throw ExportUnreadableException()
        return build(view, format, track, streams?.data, streams?.trainingLoad)
    }

    private suspend fun requireSent(view: ActivityView) {
        val rkey = requireNotNull(view.uri.rkey)
        if (db.outbox().pending(clients.did.value).any { it.rkey == rkey && it.collection in EXPORTED_COLLECTIONS }) {
            throw NotSentYetException()
        }
    }

    /** The streams and, as the owner may see the zones record, the training load stored with the zones. */
    private suspend fun readStreams(view: ActivityView): GetActivityStreams.Output? = runSuspendCatching {
        feed.streams(view.uri)
    }
        .getOrElse { throw ExportUnreadableException(it) }

    // `read` says null both for no track and for one it couldn't read, so a null asks whether there's one to read.
    private suspend fun readTrack(view: ActivityView): List<LocationSample>? {
        tracks.read(view.uri)?.let {
            return it
        }
        val has = runSuspendCatching { tracks.has(view.uri) }.getOrElse { throw ExportUnreadableException(it) }
        if (has) throw ExportUnreadableException()
        return null
    }

    @Suppress("InjectDispatcher") // CPU-bound, as charts are; files of tens of thousands of points.
    private suspend fun build(
        view: ActivityView,
        format: ExportFormat,
        track: List<LocationSample>?,
        streams: ActivityStreamData?,
        trainingLoad: TrainingLoad?,
    ): ExportedFile =
        withContext(Dispatchers.Default) {
            val record = view.record
            val activity =
                ExportActivity(
                    name = displayName(view),
                    sport = record.sport,
                    tcxSport = tcxSport(record.sport),
                    startedAt = record.startedAt,
                    elapsed = record.elapsedTime.seconds,
                    moving = record.movingTime?.seconds,
                    distanceMeters = record.distance?.toDouble(),
                    avgHeartRate = record.avgHeartRate,
                    maxHeartRate = record.maxHeartRate,
                    isDive = record.sport == Sport.DIVE,
                    points = ExportPoints.join(record.startedAt, track, streams),
                    subSport = record.subSport,
                    elevationGainMeters = record.elevationGain?.toDouble(),
                    avgSpeedMps = record.avgSpeed?.div(MILLI),
                    maxSpeedMps = record.maxSpeed?.div(MILLI),
                    avgTemperatureCelsius = record.avgTemperature?.div(DECI),
                    gearChanges = streams?.gears.orEmpty(),
                    dive = record.dive,
                    gasSwitches = streams?.gasSwitches.orEmpty(),
                    laps = streams?.laps.orEmpty(),
                    totals = ExportTotals.of(record),
                    trainingLoad = trainingLoad,
                )
            val zone = TimeZone.currentSystemDefault()
            val bytes =
                when (format) {
                    ExportFormat.GPX -> GpxWriter.write(activity).encodeToByteArray()
                    ExportFormat.TCX -> TcxWriter.write(activity).encodeToByteArray()
                    ExportFormat.FIT -> FitWriter.write(activity, zone)
                }
            ExportedFile(fileName(view, format, zone), format.mimeType, bytes)
        }

    companion object {
        private val EXPORTED_COLLECTIONS = setOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_STREAMS, Nsid.FEED_ACTIVITY_TRACK)
        private val INVALID_FILE_NAME_CHARS = Regex("""[/\\:*?"<>|\u0000-\u001F]""")
        private const val MAX_NAME_LENGTH = 80
        private const val MILLI = 1000.0
        private const val DECI = 10.0

        /** What [track] and streams allow: GPX needs a track of two points or more, TCX and FIT a track or streams. */
        fun formats(track: List<LocationSample>?, hasStreams: Boolean): Set<ExportFormat> = buildSet {
            if ((track?.size ?: 0) >= 2) add(ExportFormat.GPX)
            if (!track.isNullOrEmpty() || hasStreams) {
                add(ExportFormat.TCX)
                add(ExportFormat.FIT)
            }
        }

        /** TCX knows only running and biking; everything else is `Other`. */
        fun tcxSport(sport: String): TcxSport =
            when (SportCatalog.info(sport)?.category) {
                SportCategory.Running -> TcxSport.Running
                SportCategory.Cycling -> TcxSport.Biking
                else -> TcxSport.Other
            }

        /**
         * The title, or the sport's name, and the local start date, e.g. `Morning Run 2026-10-15.gpx`, without characters any platform
         * refuses in a file name.
         */
        fun fileName(view: ActivityView, format: ExportFormat, zone: TimeZone): String {
            val date = view.record.startedAt.toLocalDateTime(zone).date
            val name = displayName(view).replace(INVALID_FILE_NAME_CHARS, "_").trim().trimEnd('.').take(MAX_NAME_LENGTH).trim()
            return "${name.ifEmpty { format.name }} $date.${format.extension}"
        }

        /** The activity's title, else its sport's token spelt as words ("xcSki" → "Xc ski"); the data layer has no localized names. */
        private fun displayName(view: ActivityView): String =
            view.record.title?.takeIf { it.isNotBlank() }
                ?: view.record.sport
                    .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
                    .replaceFirstChar { it.uppercase() }
    }
}
