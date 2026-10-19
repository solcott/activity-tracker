package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDevices
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.StreamBuilder
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingResult
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/** An activity ready to publish, from a GPS recording, an imported file or manual entry. */
data class ActivityDraft(
    val type: ActivityType,
    val title: String?,
    val description: String?,
    val startedAt: Instant,
    val elapsed: Duration,
    val movingSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val maxSpeedMps: Double? = null,
    val avgTemperatureCelsius: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Recording device, e.g. "Garmin fenix7". */
    val device: String? = null,
    /** Paired with [device]; public, so make and model only. */
    val sensors: List<ActivitySensor> = emptyList(),
    /**
     * The GPS points, full resolution. Never published as they are: `ActivityRepository` trims them by the athlete's privacy zones for the
     * record's polyline, and the track keeps them only in the `self` space ([gpx]).
     */
    val route: List<LocationSample> = emptyList(),
    val gpx: ByteArray? = null,
    /** Per-sample data for charts, published as the activity's streams record. */
    val streams: ActivityStreamData? = null,
    /** Who may see the activity: a [Visibility] value, which decides where it's stored. */
    val visibility: String = Visibility.PUBLIC,
    /**
     * Who may see each [DetailCategory] of it, by token: [Visibility] values, each capped at [visibility] once published ([tiers]). A
     * category left out is at [visibility].
     */
    val categories: Map<String, String> = emptyMap(),
    /** The athlete's zones for this activity, kept with [streams]. */
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
    /** The dive computer's summary, for a dive. */
    val dive: ActivityDive? = null,
    /** Photos to upload with it, in order. */
    val photos: List<NewPhoto> = emptyList(),
    val elevationLossMeters: Double? = null,
    /** Revolutions or steps per minute. */
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    val avgPowerWatts: Int? = null,
    val maxPowerWatts: Int? = null,
    val normalizedPowerWatts: Int? = null,
    val workJoules: Double? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    val frontShifts: Int? = null,
    val rearShifts: Int? = null,
    /** Garmin's mountain bike dynamics. */
    val jumps: Int? = null,
    val grit: Double? = null,
    val flow: Double? = null,
    /** The recording device's training load, kept with the zones (it reveals thresholds), never on the record. */
    val trainingLoad: TrainingLoad? = null,
    /** The file it was imported from, kept as is in the `self` space ([ActivitySourceStore]); null for a recording or a manual entry. */
    val source: DraftSource? = null,
    /** The file's devices and their serial numbers, kept privately in the `self` space ([ActivityDevicesStore]); null for none. */
    val devices: ImportedDevices? = null,
    /** A pool swim's pool length, in centimeters. */
    val poolLengthCentimeters: Int? = null,
    /** The device's cycle count: steps, strokes or pushes by sport ([ActivityRecord.cycles]). */
    val cycles: Int? = null,
    val avgStrokeDistanceCentimeters: Int? = null,
    /** The UTC offset where the activity started, in minutes east of UTC. */
    val utcOffsetMinutes: Int? = null,
) {
    /**
     * This draft with what the athlete wrote about it: [description], [photos], and for a dive its [diveSite] (blank clears the logbook's;
     * null leaves the dive as it is).
     */
    fun withDetails(description: String?, diveSite: String?, photos: List<NewPhoto>): ActivityDraft =
        copy(
            description = description,
            photos = photos,
            dive =
                diveSite?.let { site ->
                    (dive ?: ActivityDive()).copy(site = site.trim().take(ActivityDive.MAX_SITE_LENGTH).ifEmpty { null })
                } ?: dive,
        )

    /** This draft shown to [choice]'s viewers: the activity at its visibility, each category capped at it. */
    fun withVisibility(choice: VisibilityChoice): ActivityDraft =
        choice.capped().let { copy(visibility = it.activity, categories = it.details) }

    /** This draft at [visibility] (a combined activity's, which its members share), its categories capped at it. */
    fun atVisibility(visibility: String): ActivityDraft =
        copy(visibility = visibility, categories = DetailCategory.tiers(visibility, tiers))

    /** Every category's visibility as published: never wider than the activity. */
    val tiers: Map<String, String>
        get() = DetailCategory.tiers(visibility, categories)

    /** The record, holding [media] ([photos] once uploaded) and [polyline] ([route] as trimmed for it). */
    fun toRecord(createdAt: Instant, media: List<ActivityMedia> = emptyList(), polyline: String? = null): ActivityRecord =
        ActivityRecord(
            sport = type.sport,
            subSport = type.subSport,
            title = title?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            startedAt = startedAt,
            elapsedTime = elapsed.inWholeSeconds,
            movingTime = movingSeconds,
            distance = distanceMeters?.roundToLong(),
            elevationGain = elevationGainMeters?.roundToLong(),
            avgSpeed =
                distanceMeters?.let { d ->
                    val seconds = movingSeconds ?: elapsed.inWholeSeconds
                    if (seconds > 0) (d / seconds * MM_PER_M).roundToLong() else null
                },
            maxSpeed = maxSpeedMps?.let { (it * MM_PER_M).roundToLong() },
            avgTemperature = avgTemperatureCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() },
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            polyline = polyline,
            device = device,
            sensors = sensors.take(ActivitySensor.MAX).ifEmpty { null },
            dive = dive,
            media = media.take(ActivityMedia.MAX).ifEmpty { null },
            createdAt = createdAt,
            elevationLoss = elevationLossMeters?.roundToLong()?.coerceAtLeast(0),
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgPower = avgPowerWatts,
            maxPower = maxPowerWatts,
            normalizedPower = normalizedPowerWatts,
            work = workJoules?.let { (it / J_PER_KJ).roundToLong() },
            calories = calories,
            frontShifts = frontShifts,
            rearShifts = rearShifts,
            jumps = jumps,
            grit = grit?.let { (it * TENTHS).roundToInt().coerceAtLeast(0) },
            flow = flow?.let { (it * TENTHS).roundToInt().coerceAtLeast(0) },
            poolLength = poolLengthCentimeters,
            cycles = cycles,
            avgStrokeDistance = avgStrokeDistanceCentimeters,
            utcOffset = utcOffsetMinutes?.takeIf { it in ActivityRecord.MIN_UTC_OFFSET..ActivityRecord.MAX_UTC_OFFSET },
        )

    // Generated equals/hashCode compare arrays by identity; compare gpx by content instead.
    private fun fieldsExceptGpx(): List<Any?> =
        listOf(
            type,
            title,
            description,
            startedAt,
            elapsed,
            movingSeconds,
            distanceMeters,
            elevationGainMeters,
            maxSpeedMps,
            avgTemperatureCelsius,
            avgHeartRate,
            maxHeartRate,
            device,
            sensors,
            route,
            streams,
            visibility,
            categories,
            heartRateZones,
            powerZones,
            dive,
            photos,
            elevationLossMeters,
            avgCadence,
            maxCadence,
            avgPowerWatts,
            maxPowerWatts,
            normalizedPowerWatts,
            workJoules,
            calories,
            frontShifts,
            rearShifts,
            jumps,
            grit,
            flow,
            trainingLoad,
            poolLengthCentimeters,
            cycles,
            avgStrokeDistanceCentimeters,
            utcOffsetMinutes,
        )

    override fun equals(other: Any?): Boolean =
        this === other || other is ActivityDraft && fieldsExceptGpx() == other.fieldsExceptGpx() && gpx.contentEquals(other.gpx)

    override fun hashCode(): Int = 31 * fieldsExceptGpx().hashCode() + gpx.contentHashCode()

    companion object {
        /** Lexicon speeds are integer mm/s. */
        private const val MM_PER_M = 1000

        /** Lexicon temperatures are integer tenths of a degree Celsius. */
        private const val TENTHS_PER_DEGREE = 10
        private const val TENTHS = 10
        private const val HUNDREDTHS = 100
        private const val J_PER_KJ = 1000.0
        private const val SECONDS_PER_MINUTE = 60

        /** A recording in the app; its UTC offset is [zone]'s (this device's) at the start. */
        fun fromRecording(
            result: RecordingResult,
            type: ActivityType,
            title: String?,
            description: String?,
            zone: TimeZone = TimeZones.device(),
        ): ActivityDraft {
            val state = result.state
            val samples = state.samples
            val startedAt = state.startedAt ?: result.endedAt
            return ActivityDraft(
                type = type,
                title = title,
                description = description,
                startedAt = startedAt,
                utcOffsetMinutes = zone.offsetAt(startedAt).totalSeconds / SECONDS_PER_MINUTE,
                elapsed = state.activeBeforeSegment,
                movingSeconds = state.movingSeconds,
                distanceMeters = state.distanceMeters,
                elevationGainMeters = state.elevationGainMeters,
                maxSpeedMps = state.maxSpeedMps,
                route = samples,
                gpx = gpxOf(samples, title, type.sport),
                streams = StreamBuilder.fromLocations(samples, startedAt),
            )
        }

        /**
         * An activity read from a file. Its track is published as GPX, like a recorded one; a dive with no GPS track gets one from where it
         * went in and came out (or its logbook site), timed at its start and end, which stays in the track and makes no polyline. Its
         * description starts from the file's notes, for the athlete to keep or change.
         */
        fun fromImport(
            imported: ImportedActivity,
            type: ActivityType,
            title: String?,
        ): ActivityDraft {
            val diveFixes = if (imported.samples.isEmpty()) imported.dive?.let { diveFixes(it, imported) }.orEmpty() else emptyList()
            return ActivityDraft(
                type = type,
                title = title,
                description = imported.description,
                startedAt = imported.startedAt,
                elapsed = imported.elapsed,
                movingSeconds = imported.movingSeconds,
                distanceMeters = imported.distanceMeters,
                elevationGainMeters = imported.elevationGainMeters,
                maxSpeedMps = imported.maxSpeedMps,
                avgTemperatureCelsius = imported.avgTemperatureCelsius,
                avgHeartRate = imported.avgHeartRate,
                maxHeartRate = imported.maxHeartRate,
                device = imported.device,
                sensors = imported.sensors.map { ActivitySensor(it.kind.token, it.manufacturer, it.model) },
                devices = imported.devices,
                route = imported.samples,
                gpx = gpxOf(imported.samples.ifEmpty { diveFixes }, title, type.sport),
                streams = imported.streams,
                dive = imported.dive?.let(DiveSummary::of),
                elevationLossMeters = imported.elevationLossMeters,
                avgCadence = imported.avgCadence,
                maxCadence = imported.maxCadence,
                avgPowerWatts = imported.power?.avgWatts,
                maxPowerWatts = imported.power?.maxWatts,
                normalizedPowerWatts = imported.power?.normalizedWatts,
                workJoules = imported.power?.workJoules,
                calories = imported.calories,
                frontShifts = imported.shifting?.frontShifts,
                rearShifts = imported.shifting?.rearShifts,
                jumps = imported.mtb?.jumps,
                grit = imported.mtb?.grit,
                flow = imported.mtb?.flow,
                trainingLoad = trainingLoadOf(imported),
                poolLengthCentimeters = imported.poolLengthCentimeters,
                cycles = imported.cycles,
                avgStrokeDistanceCentimeters = imported.avgStrokeDistanceCentimeters,
                utcOffsetMinutes = imported.utcOffsetMinutes,
            )
        }

        /** The device's FTP, IF, TSS and training effect, and the file's HRV, or null without any. */
        private fun trainingLoadOf(imported: ImportedActivity): TrainingLoad? =
            TrainingLoad(
                    ftp = imported.power?.ftpWatts?.takeIf { it > 0 },
                    intensityFactor = imported.power?.intensityFactor?.let { (it * HUNDREDTHS).roundToInt() }?.takeIf { it > 0 },
                    trainingStressScore = imported.power?.trainingStressScore?.let { (it * TENTHS).roundToInt() }?.takeIf { it > 0 },
                    aerobicTrainingEffect = imported.aerobicTrainingEffect?.let { (it * TENTHS).roundToInt() },
                    anaerobicTrainingEffect = imported.anaerobicTrainingEffect?.let { (it * TENTHS).roundToInt() },
                    hrvRmssd = imported.hrvRmssd?.takeIf { it > 0 },
                )
                .takeUnless { it.isEmpty }

        /**
         * A trackless dive's surface fixes: its entry at the start and its exit at the end. A logbook's site position comes through as its
         * entry, so it's one point.
         */
        private fun diveFixes(dive: ImportedDive, imported: ImportedActivity): List<LocationSample> =
            listOfNotNull(
                dive.entry?.let { LocationSample(it.latitude, it.longitude, null, null, null, imported.startedAt) },
                dive.exit?.let { LocationSample(it.latitude, it.longitude, null, null, null, imported.startedAt + imported.elapsed) },
            )

        /**
         * The polyline for a track: simplified, then encoded. Null below two points. Untrimmed: only the athlete's own screens draw it as
         * it is; records get `RouteTrim.polylineOf`.
         */
        fun polylineOf(samples: List<LocationSample>): String? =
            samples.takeIf { it.size >= 2 }?.let { TrackMath.encodePolyline(TrackMath.simplify(it)) }

        private fun gpxOf(samples: List<LocationSample>, title: String?, sport: String): ByteArray? =
            samples.takeIf { it.isNotEmpty() }?.let { Gpx.write(title ?: sport, sport, it).encodeToByteArray() }
    }
}
