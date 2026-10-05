package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Turns a decoded FIT, GPX or TCX file into an [ImportedActivity]. Summary totals win because the device computed them from data we don't
 * see (auto-pause, barometric altitude): FIT sessions, else laps (all TCX has). Anything the summaries leave out, which is everything for
 * GPX, is computed from the samples. A multi-session file of several sports (a triathlon, say) imports as one multisport activity.
 */
object DecodedActivityMapper {
    fun map(fit: DecodedActivity): ImportedActivity {
        val type = fit.fileId?.type?.fitKey()
        if (type != null && type != "activity") activityFileError("Not an activity file: ${fit.fileId.type}")

        val records = fit.records.filter { it.timestampMillis != null }.sortedBy { it.timestampMillis }
        val sessions = fit.sessions.ifEmpty { fit.laps }
        if (sessions.isEmpty() && records.isEmpty()) activityFileError("No activity data in file")

        val first = sessions.firstOrNull()
        val startedAtMillis =
            first?.startTimeMillis
                ?: records.firstOrNull()?.timestampMillis
                ?: fit.fileId?.timeCreatedMillis
                ?: activityFileError("No start time in file")
        val samples = records.toSamples()

        return ImportedActivity(
            type = activityType(sessions),
            startedAt = Instant.fromEpochMilliseconds(startedAtMillis),
            elapsed = elapsed(sessions, records),
            movingSeconds = (sessions.total { it.totalTimerSeconds } ?: samples.movingSeconds())?.roundToLong(),
            distanceMeters = distanceMeters(sessions, records, samples),
            elevationGainMeters = sessions.total { it.totalAscentMeters } ?: records.mapNotNull { it.altitudeMeters }.climb(),
            maxSpeedMps = sessions.mapNotNull { it.maxSpeedMps }.maxOrNull() ?: records.mapNotNull { it.speedMps }.maxOrNull(),
            avgHeartRate = sessions.weightedAverage { it.avgHeartRate?.toDouble() }?.roundToInt() ?: records.averageOf { it.heartRate },
            maxHeartRate = sessions.mapNotNull { it.maxHeartRate }.maxOrNull() ?: records.mapNotNull { it.heartRate }.maxOrNull(),
            avgTemperatureCelsius = avgTemperature(sessions, records),
            device = fit.fileId?.let { device(it.manufacturer, it.product) },
            samples = samples,
        )
    }

    private fun avgTemperature(sessions: List<DecodedSummary>, records: List<DecodedSample>): Double? =
        sessions.weightedAverage { it.avgTemperatureCelsius }
            ?: records.mapNotNull { it.temperatureCelsius }.takeIf { it.isNotEmpty() }?.average()

    /** [records] are sorted and all have a timestamp. */
    private fun elapsed(sessions: List<DecodedSummary>, records: List<DecodedSample>): Duration {
        val first = records.firstOrNull()?.timestampMillis ?: 0L
        val last = records.lastOrNull()?.timestampMillis ?: 0L
        return sessions.total { it.totalElapsedSeconds }?.seconds ?: (last - first).milliseconds
    }

    private fun distanceMeters(sessions: List<DecodedSummary>, records: List<DecodedSample>, samples: List<LocationSample>): Double? =
        sessions.total { it.totalDistanceMeters }
            ?: records.lastNotNullOf { it.distanceMeters }
            ?: samples.zipWithNext(TrackMath::distanceMeters).sum().takeIf { samples.size >= 2 }

    /**
     * Maps a sport name in any format's spelling (FIT enum, TCX `Sport`, GPX `type` including Strava's names) and a FIT sub-sport to an
     * activity type. The sub-sport refines a known sport, or on its own names the type when the sport is generic (FIT's `training`).
     */
    fun activityType(sport: String?, subSport: String? = null): ActivityType {
        val sub = subSport?.fitKey()?.takeUnless { it in GENERIC_SUB_SPORTS }
        val base = sport?.fitKey()?.let(SPORTS::get)
        return when {
            base != null -> sub?.let { SUB_SPORTS[base.sport]?.get(it) } ?: base
            sub != null -> STANDALONE_SUB_SPORTS[sub] ?: OTHER
            else -> OTHER
        }
    }

    /** One activity per file, so a file whose sessions are different sports (a triathlon, say) is a multisport activity. */
    private fun activityType(sessions: List<DecodedSummary>): ActivityType {
        val types = sessions.map { activityType(it.sport, it.subSport) }.filter { it.sport != TRANSITION }
        val sports = types.map { it.sport }.toSet()
        if (sports.size <= 1) return types.firstOrNull() ?: OTHER
        val legs = sports.filter { it == Sport.SWIM || it == Sport.RIDE || it == Sport.RUN }.toSet()
        return ActivityType(
            Sport.MULTISPORT,
            when {
                legs.size != sports.size -> SubSport.MIXED
                legs == setOf(Sport.SWIM, Sport.RIDE, Sport.RUN) -> SubSport.TRIATHLON
                legs == setOf(Sport.RUN, Sport.RIDE) -> SubSport.DUATHLON
                legs == setOf(Sport.SWIM, Sport.RUN) -> SubSport.SWIMRUN
                else -> SubSport.MIXED
            },
        )
    }

    private val OTHER = ActivityType(Sport.OTHER)

    /** Our sentinel for FIT's `transition` sport, the time between multisport legs. */
    private const val TRANSITION = "transition"

    private val GENERIC_SUB_SPORTS = setOf("generic", "all", "invalid")

    private fun type(sport: String, subSport: String? = null) = ActivityType(sport, subSport)

    /** Sport names, normalized by [fitKey], to the type they name. */
    private val SPORTS: Map<String, ActivityType> =
        listOf(
                type(Sport.RUN) to listOf("running", "run"),
                type(Sport.RUN, SubSport.TRAIL) to listOf("trailrunning", "trailrun"),
                type(Sport.RUN, SubSport.VIRTUAL) to listOf("virtualrun"),
                type(Sport.RUN, SubSport.TREADMILL) to listOf("treadmillrunning"),
                type(Sport.RIDE) to listOf("cycling", "biking", "ride"),
                type(Sport.RIDE, SubSport.ROAD) to listOf("roadbiking", "roadcycling"),
                type(Sport.RIDE, SubSport.MOUNTAIN) to listOf("mountainbiking", "mountainbikeride"),
                type(Sport.RIDE, SubSport.GRAVEL) to listOf("gravelcycling", "gravelride"),
                type(Sport.RIDE, SubSport.VIRTUAL) to listOf("virtualride"),
                type(Sport.RIDE, SubSport.E_BIKE) to listOf("ebiking", "ebikeride"),
                type(Sport.RIDE, SubSport.E_MOUNTAIN) to listOf("emountainbikeride"),
                type(Sport.RIDE, SubSport.CYCLOCROSS) to listOf("cyclocross"),
                type(Sport.HANDCYCLE) to listOf("handcycle", "handcycling"),
                type(Sport.SWIM) to listOf("swimming", "swim"),
                type(Sport.SWIM, SubSport.OPEN_WATER) to listOf("openwaterswimming"),
                type(Sport.SWIM, SubSport.POOL) to listOf("lapswimming", "poolswimming"),
                type(Sport.MULTISPORT) to listOf("multisport"),
                type(Sport.MULTISPORT, SubSport.TRIATHLON) to listOf("triathlon"),
                type(Sport.MULTISPORT, SubSport.DUATHLON) to listOf("duathlon"),
                type(TRANSITION) to listOf("transition"),
                type(Sport.WALK) to listOf("walking", "walk"),
                type(Sport.HIKE) to listOf("hiking", "hike"),
                type(Sport.MOUNTAINEERING) to listOf("mountaineering"),
                type(Sport.CLIMB) to listOf("rockclimbing", "rockclimb", "climbing"),
                type(Sport.INLINE_SKATE) to listOf("inlineskating", "inlineskate"),
                type(Sport.HORSEBACK) to listOf("horsebackriding", "horseback"),
                type(Sport.GOLF) to listOf("golf"),
                type(Sport.DISC_GOLF) to listOf("discgolf"),
                type(Sport.ARCHERY) to listOf("archery"),
                type(Sport.HUNT) to listOf("hunting", "hunt"),
                type(Sport.FISH) to listOf("fishing", "fish"),
                type(Sport.SKI) to listOf("alpineskiing", "alpineski", "downhillskiing"),
                type(Sport.SKI, SubSport.BACKCOUNTRY) to listOf("backcountryskiing", "backcountryski"),
                type(Sport.SNOWBOARD) to listOf("snowboarding", "snowboard"),
                type(Sport.XC_SKI) to listOf("crosscountryskiing", "nordicski"),
                type(Sport.SNOWSHOE) to listOf("snowshoeing", "snowshoe"),
                type(Sport.ICE_SKATE) to listOf("iceskating", "iceskate"),
                type(Sport.ROW) to listOf("rowing", "row"),
                type(Sport.KAYAK) to listOf("kayaking", "paddling", "canoeing"),
                type(Sport.SUP) to listOf("standuppaddleboarding", "standuppaddling"),
                type(Sport.SURF) to listOf("surfing", "surf"),
                type(Sport.KITEBOARD) to listOf("kitesurfing", "kitesurf", "kiteboarding"),
                type(Sport.WINDSURF) to listOf("windsurfing", "windsurf"),
                type(Sport.SAIL) to listOf("sailing", "sail"),
                type(Sport.WAKEBOARD) to listOf("wakeboarding"),
                type(Sport.WAKESURF) to listOf("wakesurfing"),
                type(Sport.WATER_SKI) to listOf("waterskiing"),
                type(Sport.WHITEWATER) to listOf("rafting", "whitewater"),
                type(Sport.TUBE) to listOf("watertubing", "tubing"),
                type(Sport.SNORKEL) to listOf("snorkeling"),
                type(Sport.BOAT) to listOf("boating"),
                type(Sport.DIVE) to listOf("diving", "scubadiving"),
                type(Sport.STRENGTH) to listOf("weighttraining", "strengthtraining"),
                type(Sport.CARDIO) to listOf("workout"),
                type(Sport.HIIT) to listOf("hiit", "crossfit"),
                type(Sport.YOGA) to listOf("yoga"),
                type(Sport.PILATES) to listOf("pilates"),
                type(Sport.ELLIPTICAL) to listOf("elliptical"),
                type(Sport.STAIR_STEPPER) to listOf("stairstepper"),
                type(Sport.FLOOR_CLIMB) to listOf("floorclimbing"),
                type(Sport.JUMP_ROPE) to listOf("jumprope"),
                type(Sport.BOXING) to listOf("boxing"),
                type(Sport.MMA) to listOf("mixedmartialarts"),
                type(Sport.DANCE) to listOf("dance"),
                type(Sport.SOCCER) to listOf("soccer"),
                type(Sport.AMERICAN_FOOTBALL) to listOf("americanfootball"),
                type(Sport.BASEBALL) to listOf("baseball"),
                type(Sport.SOFTBALL) to listOf("softball"),
                type(Sport.BASKETBALL) to listOf("basketball"),
                type(Sport.VOLLEYBALL) to listOf("volleyball"),
                type(Sport.ICE_HOCKEY) to listOf("hockey", "icehockey"),
                type(Sport.FIELD_HOCKEY) to listOf("fieldhockey"),
                type(Sport.LACROSSE) to listOf("lacrosse"),
                type(Sport.RUGBY) to listOf("rugby"),
                type(Sport.CRICKET) to listOf("cricket"),
                type(Sport.ULTIMATE) to listOf("ultimate", "ultimatedisc"),
                type(Sport.TENNIS) to listOf("tennis", "racket"),
                type(Sport.PICKLEBALL) to listOf("pickleball"),
                type(Sport.PADEL) to listOf("padel"),
                type(Sport.BADMINTON) to listOf("badminton"),
                type(Sport.SQUASH) to listOf("squash"),
                type(Sport.RACQUETBALL) to listOf("racquetball"),
                type(Sport.TABLE_TENNIS) to listOf("tabletennis"),
                type(Sport.MOTORCYCLE) to listOf("motorcycling", "motorcycle"),
                type(Sport.SNOWMOBILE) to listOf("snowmobiling", "snowmobile"),
                type(Sport.MEDITATION) to listOf("meditation"),
                type(Sport.FLYING) to listOf("flying"),
                type(Sport.SKYDIVE) to listOf("skydiving", "jumpmaster"),
                type(Sport.OTHER) to listOf("other"),
            )
            .flatMap { (type, names) -> names.map { it to type } }
            .toMap()

    /**
     * FIT sub-sports, per the sport they refine, to the type they name. A sub-sport can move the activity to another sport (a hockey
     * match).
     */
    private val SUB_SPORTS: Map<String, Map<String, ActivityType>> =
        mapOf(
            Sport.RUN to
                mapOf(
                    "trail" to type(Sport.RUN, SubSport.TRAIL),
                    "treadmill" to type(Sport.RUN, SubSport.TREADMILL),
                    "track" to type(Sport.RUN, SubSport.TRACK),
                    "indoorrunning" to type(Sport.RUN, SubSport.INDOOR_TRACK),
                    "virtualactivity" to type(Sport.RUN, SubSport.VIRTUAL),
                    "ultra" to type(Sport.RUN, SubSport.ULTRA),
                    "obstacle" to type(Sport.RUN, SubSport.OBSTACLE),
                ),
            Sport.RIDE to
                mapOf(
                    "road" to type(Sport.RIDE, SubSport.ROAD),
                    "mountain" to type(Sport.RIDE, SubSport.MOUNTAIN),
                    "downhill" to type(Sport.RIDE, SubSport.MOUNTAIN),
                    "gravelcycling" to type(Sport.RIDE, SubSport.GRAVEL),
                    "mixedsurface" to type(Sport.RIDE, SubSport.GRAVEL),
                    "cyclocross" to type(Sport.RIDE, SubSport.CYCLOCROSS),
                    "indoorcycling" to type(Sport.RIDE, SubSport.INDOOR),
                    "spin" to type(Sport.RIDE, SubSport.INDOOR),
                    "virtualactivity" to type(Sport.RIDE, SubSport.VIRTUAL),
                    "ebikefitness" to type(Sport.RIDE, SubSport.E_BIKE),
                    "ebikemountain" to type(Sport.RIDE, SubSport.E_MOUNTAIN),
                    "commuting" to type(Sport.RIDE, SubSport.COMMUTE),
                    "biketour" to type(Sport.RIDE, SubSport.TOUR),
                    "bmx" to type(Sport.RIDE, SubSport.BMX),
                    "handcycling" to type(Sport.HANDCYCLE),
                    "indoorhandcycling" to type(Sport.HANDCYCLE, SubSport.INDOOR),
                ),
            Sport.SWIM to mapOf("lapswimming" to type(Sport.SWIM, SubSport.POOL), "openwater" to type(Sport.SWIM, SubSport.OPEN_WATER)),
            Sport.WALK to mapOf("indoorwalking" to type(Sport.WALK, SubSport.INDOOR), "treadmill" to type(Sport.WALK, SubSport.TREADMILL)),
            Sport.CLIMB to
                mapOf("bouldering" to type(Sport.CLIMB, SubSport.BOULDERING), "indoorclimbing" to type(Sport.CLIMB, SubSport.INDOOR)),
            Sport.SKI to mapOf("backcountry" to type(Sport.SKI, SubSport.BACKCOUNTRY)),
            Sport.SNOWBOARD to mapOf("backcountry" to type(Sport.SNOWBOARD, SubSport.BACKCOUNTRY)),
            Sport.XC_SKI to
                mapOf(
                    "skateskiing" to type(Sport.XC_SKI, SubSport.SKATE),
                    "classicskiing" to type(Sport.XC_SKI, SubSport.CLASSIC),
                    "indoorskiing" to type(Sport.XC_SKI, SubSport.INDOOR),
                ),
            Sport.ROW to mapOf("indoorrowing" to type(Sport.ROW, SubSport.INDOOR)),
            Sport.SAIL to mapOf("sailrace" to type(Sport.SAIL, SubSport.RACE), "sailexpedition" to type(Sport.SAIL, SubSport.EXPEDITION)),
            Sport.DIVE to
                mapOf(
                    "singlegasdiving" to type(Sport.DIVE, SubSport.SINGLE_GAS),
                    "multigasdiving" to type(Sport.DIVE, SubSport.MULTI_GAS),
                    "ccrdiving" to type(Sport.DIVE, SubSport.CCR),
                    "gaugediving" to type(Sport.DIVE, SubSport.GAUGE),
                    "apneadiving" to type(Sport.DIVE, SubSport.APNEA),
                    "apneahunting" to type(Sport.DIVE, SubSport.APNEA_HUNT),
                    "poolapnea" to type(Sport.DIVE, SubSport.POOL_APNEA),
                ),
            Sport.ICE_HOCKEY to mapOf("fieldhockey" to type(Sport.FIELD_HOCKEY)),
            Sport.TENNIS to
                mapOf(
                    "pickleball" to type(Sport.PICKLEBALL),
                    "padel" to type(Sport.PADEL),
                    "badminton" to type(Sport.BADMINTON),
                    "squash" to type(Sport.SQUASH),
                    "racquetball" to type(Sport.RACQUETBALL),
                    "tabletennis" to type(Sport.TABLE_TENNIS),
                    "platformtennis" to type(Sport.PLATFORM_TENNIS),
                ),
            Sport.MOTORCYCLE to mapOf("motocross" to type(Sport.MOTOCROSS), "atv" to type(Sport.ATV)),
            Sport.MULTISPORT to mapOf("triathlon" to type(Sport.MULTISPORT, SubSport.TRIATHLON)),
        )

    /** FIT sub-sports that name the type on their own, under a generic sport such as `training` or `fitness_equipment`. */
    private val STANDALONE_SUB_SPORTS: Map<String, ActivityType> =
        mapOf(
            "strengthtraining" to type(Sport.STRENGTH),
            "cardiotraining" to type(Sport.CARDIO),
            "yoga" to type(Sport.YOGA),
            "pilates" to type(Sport.PILATES),
            "hiit" to type(Sport.HIIT),
            "breathing" to type(Sport.BREATHWORK),
            "flexibilitytraining" to type(Sport.MOBILITY),
            "mobility" to type(Sport.MOBILITY),
            "elliptical" to type(Sport.ELLIPTICAL),
            "stairclimbing" to type(Sport.STAIR_STEPPER),
            "indoorrowing" to type(Sport.ROW, SubSport.INDOOR),
            "indoorcycling" to type(Sport.RIDE, SubSport.INDOOR),
            "virtualactivity" to type(Sport.RIDE, SubSport.VIRTUAL),
            "treadmill" to type(Sport.RUN, SubSport.TREADMILL),
            "trail" to type(Sport.RUN, SubSport.TRAIL),
            "indoorwalking" to type(Sport.WALK, SubSport.INDOOR),
            "indoorclimbing" to type(Sport.CLIMB, SubSport.INDOOR),
            "bouldering" to type(Sport.CLIMB, SubSport.BOULDERING),
            "indoorskiing" to type(Sport.XC_SKI, SubSport.INDOOR),
        )

    /**
     * "garmin" + "fenix7" → "Garmin fenix7"; FIT names are snake_case in Java, camelCase in JS. A display name with spaces (TCX's
     * "Forerunner 965") is kept as written.
     */
    private fun device(manufacturer: String?, product: String?): String? =
        listOfNotNull(
                manufacturer?.takeUnless { it.fitKey() in setOf("unknown", "invalid") }?.humanize()?.replaceFirstChar { it.uppercase() },
                product?.humanize(),
            )
            .joinToString(" ")
            .ifBlank { null }

    private fun List<DecodedSample>.toSamples(): List<LocationSample> = mapNotNull { r ->
        val lat = r.latitude ?: return@mapNotNull null
        val lon = r.longitude ?: return@mapNotNull null
        LocationSample(lat, lon, r.altitudeMeters, r.speedMps, accuracy = null, time = Instant.fromEpochMilliseconds(r.timestampMillis!!))
    }
}

/** Same as the recorder's: altitude only counts as climb once it moves more than this, so sensor noise doesn't add up. */
private const val ELEVATION_HYSTERESIS_M = 3.0

/** Same as the recorder's: slower than this between two samples counts as stopped. */
private const val MOVING_SPEED_MPS = 0.5

/** Time spent moving, for files with no summary (GPX). Null below two samples. */
private fun List<LocationSample>.movingSeconds(): Double? = takeIf {
    size >= 2
}
    ?.zipWithNext { a, b ->
        val seconds = (b.time - a.time).inWholeMilliseconds / MILLIS_PER_SECOND
        if (seconds > 0 && TrackMath.distanceMeters(a, b) / seconds >= MOVING_SPEED_MPS) seconds else 0.0
    }
    ?.sum()

private const val MILLIS_PER_SECOND = 1000.0

private fun List<Double>.climb(): Double? {
    if (isEmpty()) return null
    var base = first()
    var gain = 0.0
    for (altitude in drop(1)) {
        if (abs(altitude - base) > ELEVATION_HYSTERESIS_M) {
            if (altitude > base) gain += altitude - base
            base = altitude
        }
    }
    return gain
}

/** Null unless at least one session has the value. */
private inline fun List<DecodedSummary>.total(value: (DecodedSummary) -> Double?): Double? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

/** Average across sessions, weighted by timer time (falling back to equal weights). */
private inline fun List<DecodedSummary>.weightedAverage(value: (DecodedSummary) -> Double?): Double? {
    val pairs = mapNotNull { s -> value(s)?.let { it to (s.totalTimerSeconds ?: 1.0) } }
    val weight = pairs.sumOf { it.second }
    return if (pairs.isEmpty() || weight <= 0) null else pairs.sumOf { it.first * it.second } / weight
}

private inline fun List<DecodedSample>.averageOf(value: (DecodedSample) -> Int?): Int? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private inline fun <T : Any> List<DecodedSample>.lastNotNullOf(value: (DecodedSample) -> T?): T? = asReversed().firstNotNullOfOrNull(value)

private fun String.fitKey() = filter { it.isLetterOrDigit() }.lowercase()

private fun String.humanize() =
    if (' ' in trim()) trim()
    else replace('_', ' ').replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().takeUnless { it.isBlank() || it == "unknown" }
