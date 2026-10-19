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
 * GPX, is computed from the samples. A multi-session file of several sports (a triathlon, say) imports as one multisport activity, its
 * sessions kept as its legs. Laps (FIT's and TCX's alike), a pool swim's lengths and a workout's sets go into the streams; a ski day's run
 * and lift splits take the place of its laps.
 */
object DecodedActivityMapper {
    fun map(fit: DecodedActivity): ImportedActivity {
        val type = fit.fileId?.type?.fitKey()
        if (type != null && type != "activity") activityFileError("Not an activity file: ${fit.fileId.type}")

        // Stryd's developer fields, then heart rate a strap stored in hr messages, fill what the records lack.
        val records =
            HrMessages.merge(
                fit.hrMessages,
                fit.records.filter { it.timestampMillis != null }.sortedBy { it.timestampMillis }.map(DeveloperFields::apply),
            )
        val sessions = fit.sessions.ifEmpty { fit.laps }
        if (sessions.isEmpty() && records.isEmpty()) activityFileError("No activity data in file")

        val startedAtMillis = startedAtMillis(fit, sessions, records)
        val samples = records.toSamples()
        val elapsed = elapsed(sessions, records)
        val (avgCadence, maxCadence) = RideMetrics.cadence(sessions, records)
        val activityType = activityType(sessions).forGasesBreathed(fit.dive)

        return ImportedActivity(
            type = activityType,
            startedAt = Instant.fromEpochMilliseconds(startedAtMillis),
            elapsed = elapsed,
            movingSeconds = (sessions.total { it.totalTimerSeconds } ?: samples.movingSeconds())?.roundToLong(),
            distanceMeters = distanceMeters(sessions, records, samples, isDive = fit.dive != null),
            elevationGainMeters = sessions.total { it.totalAscentMeters } ?: records.mapNotNull { it.altitudeMeters }.climb(),
            maxSpeedMps = sessions.mapNotNull { it.maxSpeedMps }.maxOrNull() ?: records.mapNotNull { it.speedMps }.maxOrNull(),
            avgHeartRate = sessions.weightedAverage { it.avgHeartRate?.toDouble() }?.roundToInt() ?: records.averageOf { it.heartRate },
            maxHeartRate = sessions.mapNotNull { it.maxHeartRate }.maxOrNull() ?: records.mapNotNull { it.heartRate }.maxOrNull(),
            avgTemperatureCelsius = avgTemperature(sessions, records),
            device = fit.fileId?.let { DeviceNames.device(it.manufacturer, it.product) },
            samples = samples,
            dive = fit.dive?.let { dive(it, records, startedAtMillis) },
            localStart = fit.localStart,
            title = fit.title?.trim()?.ifEmpty { null },
            elevationLossMeters = RideMetrics.descentMeters(sessions, records),
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            power = RideMetrics.power(sessions, records),
            shifting =
                RideMetrics.shifting(
                    sessions,
                    fit.gearChanges,
                    endMillis = records.lastOrNull()?.timestampMillis ?: (startedAtMillis + elapsed.inWholeMilliseconds),
                ),
            mtb = RideMetrics.mtb(sessions, fit.jumps),
            sensors = FitSensors.interpret(fit.sensors),
            devices = FitDevices.interpret(fit.fileId, fit.sensors, fit.sessions),
            calories = sessions.mapNotNull { it.totalCalories }.takeIf { it.isNotEmpty() }?.sum()?.takeIf { it > 0 },
            aerobicTrainingEffect = sessions.mapNotNull { it.aerobicTrainingEffect }.maxOrNull()?.takeIf { it > 0 },
            anaerobicTrainingEffect = sessions.mapNotNull { it.anaerobicTrainingEffect }.maxOrNull()?.takeIf { it > 0 },
            description = fit.notes?.trim()?.ifEmpty { null },
            deviceThresholds = DeviceThresholds.interpret(fit.athlete, fit.sessions),
            streams = streams(fit, records, startedAtMillis, elapsed, activityType),
            poolLengthCentimeters =
                fit.sessions.firstNotNullOfOrNull { it.poolLengthMeters?.takeIf { m -> m > 0 } }?.let { (it * CENTI).roundToInt() },
            hrvRmssd = Hrv.rmssd(fit.rrIntervalsSeconds),
            cycles = SessionExtras.cycles(fit.sessions, activityType),
            avgStrokeDistanceCentimeters = SessionExtras.avgStrokeDistanceCentimeters(fit.sessions),
            utcOffsetMinutes = SessionExtras.utcOffsetMinutes(fit, startedAtMillis),
        )
    }

    /** Everything [fit] holds over time; a multi-session file's sessions are its legs. [records] are sorted and all have a timestamp. */
    private fun streams(fit: DecodedActivity, records: List<DecodedSample>, startedAtMillis: Long, elapsed: Duration, type: ActivityType) =
        StreamBuilder.fromRecords(
            records,
            startedAtMillis,
            fit.gearChanges,
            gasSwitches = fit.gasSwitches,
            gases = fit.dive?.gases.orEmpty(),
            tanks = fit.dive?.tanks.orEmpty(),
            tankUpdates = fit.tankUpdates,
            diveAlerts = fit.diveAlerts,
            // A ski or snowboard day's runs and lift rides, when the file split it into them, take the place of its laps.
            laps =
                if (type.sport == Sport.SKI || type.sport == Sport.SNOWBOARD)
                    fit.splits.mapNotNull(WorkoutStructure::skiLap).ifEmpty { fit.laps }
                else fit.laps,
            elapsedSeconds = elapsed.inWholeMilliseconds / MILLIS_PER_SECOND,
            lengths = fit.lengths,
            sets = fit.sets,
            legs = fit.sessions.takeIf { it.size > 1 }.orEmpty(),
        )

    /** [records] are sorted and all have a timestamp. */
    private fun dive(dive: DecodedDive, records: List<DecodedSample>, startedAtMillis: Long): ImportedDive {
        val profile = records.mapNotNull { r ->
            val millis = r.timestampMillis ?: return@mapNotNull null
            r.depthMeters?.let { DepthPoint((millis - startedAtMillis).milliseconds.inWholeSeconds, it) }
        }
        return ImportedDive(
            maxDepthMeters = dive.maxDepthMeters ?: profile.maxOfOrNull { it.depthMeters },
            avgDepthMeters = dive.avgDepthMeters ?: profile.averageDepth(),
            minWaterTempCelsius = records.mapNotNull { it.temperatureCelsius }.minOrNull(),
            diveNumber = dive.diveNumber,
            surfaceIntervalMinutes = dive.surfaceIntervalMinutes,
            gradientFactorLow = dive.gradientFactorLow,
            gradientFactorHigh = dive.gradientFactorHigh,
            startCns = dive.startCns,
            endCns = dive.endCns,
            gases = dive.gases,
            closedCircuit = dive.closedCircuit,
            profile = profile,
            bottomTimeSeconds = dive.bottomTimeSeconds,
            oxygenToxicityOtu = dive.oxygenToxicityOtu,
            rmvLitersPerMinute = dive.rmvLitersPerMinute,
            sacBarPerMinute = dive.sacBarPerMinute,
            tanks = dive.tanks,
            entry = dive.entry,
            siteName = dive.siteName,
            exit = dive.exit,
            startN2 = dive.startN2,
            endN2 = dive.endN2,
            avgAscentRateMps = dive.avgAscentRateMps,
            maxAscentRateMps = dive.maxAscentRateMps,
            avgDescentRateMps = dive.avgDescentRateMps,
            maxDescentRateMps = dive.maxDescentRateMps,
            descentTimeSeconds = dive.descentTimeSeconds,
            waterType = dive.waterType,
            waterDensityKgPerM3 = dive.waterDensityKgPerM3,
            airTemperatureCelsius = dive.airTemperatureCelsius,
            visibilityMeters = dive.visibilityMeters,
            altitudeMeters = dive.altitudeMeters,
            noFlyTimeSeconds = dive.noFlyTimeSeconds,
            desaturationTimeSeconds = dive.desaturationTimeSeconds,
            highestPpO2Bar = dive.highestPpO2Bar,
            decoModel = dive.decoModel,
            ppO2WarnBar = dive.ppO2WarnBar,
            ppO2CriticalBar = dive.ppO2CriticalBar,
            ppO2DecoBar = dive.ppO2DecoBar,
            lowSetpointBar = dive.lowSetpointBar,
            highSetpointBar = dive.highSetpointBar,
            safetyStopSeconds = dive.safetyStopSeconds,
        )
    }

    private fun startedAtMillis(fit: DecodedActivity, sessions: List<DecodedSummary>, records: List<DecodedSample>): Long =
        sessions.firstOrNull()?.startTimeMillis
            ?: records.firstOrNull()?.timestampMillis
            ?: fit.fileId?.timeCreatedMillis
            ?: activityFileError("No start time in file")

    private fun avgTemperature(sessions: List<DecodedSummary>, records: List<DecodedSample>): Double? =
        sessions.weightedAverage { it.avgTemperatureCelsius }
            ?: records.mapNotNull { it.temperatureCelsius }.takeIf { it.isNotEmpty() }?.average()

    /** [records] are sorted and all have a timestamp. */
    private fun elapsed(sessions: List<DecodedSummary>, records: List<DecodedSample>): Duration {
        val first = records.firstOrNull()?.timestampMillis ?: 0L
        val last = records.lastOrNull()?.timestampMillis ?: 0L
        return sessions.total { it.totalElapsedSeconds }?.seconds ?: (last - first).milliseconds
    }

    /** Dive computers log a distance of 0: they don't track one under water. */
    private fun distanceMeters(
        sessions: List<DecodedSummary>,
        records: List<DecodedSample>,
        samples: List<LocationSample>,
        isDive: Boolean,
    ): Double? =
        (sessions.total { it.totalDistanceMeters }
                ?: records.lastNotNullOf { it.distanceMeters }
                ?: samples.zipWithNext(TrackMath::distanceMeters).sum().takeIf { samples.size >= 2 })
            ?.takeUnless { isDive && it == 0.0 }

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
                type(Sport.RIDE, SubSport.VELOMOBILE) to listOf("velomobile"),
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
                type(Sport.WATER_FITNESS) to listOf("waterfitness", "aquaaerobics", "aquafitness"),
                type(Sport.MULTISPORT) to listOf("multisport"),
                type(Sport.MULTISPORT, SubSport.TRIATHLON) to listOf("triathlon"),
                type(Sport.MULTISPORT, SubSport.DUATHLON) to listOf("duathlon"),
                type(TRANSITION) to listOf("transition"),
                type(Sport.WALK) to listOf("walking", "walk"),
                type(Sport.WALK, SubSport.NORDIC) to listOf("nordicwalking"),
                type(Sport.WALK, SubSport.DOG) to listOf("dogwalking"),
                type(Sport.RUN, SubSport.STROLLER) to listOf("strollerrun", "strollerrunning"),
                type(Sport.WALK, SubSport.STROLLER) to listOf("strollerwalk", "strollerwalking"),
                type(Sport.WHEELCHAIR) to listOf("wheelchair"),
                type(Sport.WHEELCHAIR, SubSport.WALK_PACE) to listOf("wheelchairpushwalk", "wheelchairwalkpace"),
                type(Sport.WHEELCHAIR, SubSport.RUN_PACE) to listOf("wheelchairpushrun", "wheelchairrunpace"),
                type(Sport.SKATEBOARD) to listOf("skateboarding", "skateboard"),
                type(Sport.KICK_SCOOTER) to listOf("kickscooter", "kickscooting", "kickbike", "kickbiking"),
                type(Sport.ORIENTEERING) to listOf("orienteering"),
                type(Sport.PARKOUR) to listOf("parkour"),
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
                type(Sport.XC_SKI, SubSport.ROLLER) to listOf("rollerski", "rollerskiing"),
                type(Sport.SNOWSHOE) to listOf("snowshoeing", "snowshoe"),
                type(Sport.ICE_SKATE) to listOf("iceskating", "iceskate"),
                type(Sport.CURLING) to listOf("curling"),
                type(Sport.ROW) to listOf("rowing", "row"),
                type(Sport.ROW, SubSport.VIRTUAL) to listOf("virtualrow"),
                type(Sport.KAYAK) to listOf("kayaking", "paddling"),
                type(Sport.CANOE) to listOf("canoeing", "canoe"),
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
                type(Sport.STRENGTH, SubSport.FUNCTIONAL) to listOf("functionalstrengthtraining"),
                type(Sport.STRENGTH, SubSport.CROSSFIT) to listOf("crossfit"),
                type(Sport.STRENGTH, SubSport.CALISTHENICS) to listOf("calisthenics"),
                type(Sport.STRENGTH, SubSport.CORE) to listOf("coretraining"),
                type(Sport.CARDIO) to listOf("workout"),
                type(Sport.CARDIO, SubSport.STEP) to listOf("steptraining", "stepaerobics"),
                type(Sport.HIIT) to listOf("hiit", "highintensityintervaltraining"),
                type(Sport.HIIT, SubSport.BOOTCAMP) to listOf("bootcamp"),
                type(Sport.HIIT, SubSport.CIRCUIT) to listOf("circuittraining"),
                type(Sport.YOGA) to listOf("yoga"),
                type(Sport.PILATES) to listOf("pilates"),
                type(Sport.PILATES, SubSport.BARRE) to listOf("barre"),
                type(Sport.MOBILITY) to listOf("mobility", "flexibility"),
                type(Sport.MOBILITY, SubSport.STRETCHING) to listOf("stretching"),
                type(Sport.MOBILITY, SubSport.COOLDOWN) to listOf("cooldown"),
                type(Sport.RECOVERY) to listOf("recovery", "preparationandrecovery"),
                type(Sport.RECOVERY, SubSport.SAUNA) to listOf("sauna"),
                type(Sport.RECOVERY, SubSport.COLD_PLUNGE) to listOf("coldplunge", "icebath"),
                type(Sport.ELLIPTICAL) to listOf("elliptical"),
                type(Sport.STAIR_STEPPER) to listOf("stairstepper"),
                type(Sport.FLOOR_CLIMB) to listOf("floorclimbing"),
                type(Sport.JUMP_ROPE) to listOf("jumprope"),
                type(Sport.BOXING) to listOf("boxing"),
                type(Sport.BOXING, SubSport.KICKBOXING) to listOf("kickboxing"),
                type(Sport.MMA) to listOf("mixedmartialarts"),
                type(Sport.WRESTLING) to listOf("wrestling"),
                type(Sport.FENCING) to listOf("fencing"),
                type(Sport.TAI_CHI) to listOf("taichi"),
                type(Sport.DANCE) to listOf("dance", "dancing"),
                type(Sport.DANCE, SubSport.CARDIO) to listOf("cardiodance", "dancefitness"),
                type(Sport.DANCE, SubSport.SOCIAL) to listOf("socialdance"),
                type(Sport.SOCCER) to listOf("soccer"),
                type(Sport.SOCCER, SubSport.FUTSAL) to listOf("futsal"),
                type(Sport.AMERICAN_FOOTBALL) to listOf("americanfootball"),
                type(Sport.BASEBALL) to listOf("baseball"),
                type(Sport.SOFTBALL) to listOf("softball"),
                type(Sport.BASKETBALL) to listOf("basketball"),
                type(Sport.VOLLEYBALL) to listOf("volleyball"),
                type(Sport.VOLLEYBALL, SubSport.BEACH) to listOf("beachvolleyball"),
                type(Sport.ICE_HOCKEY) to listOf("hockey", "icehockey"),
                type(Sport.FIELD_HOCKEY) to listOf("fieldhockey"),
                type(Sport.LACROSSE) to listOf("lacrosse"),
                type(Sport.RUGBY) to listOf("rugby"),
                type(Sport.CRICKET) to listOf("cricket"),
                type(Sport.ULTIMATE) to listOf("ultimate", "ultimatedisc"),
                type(Sport.HANDBALL) to listOf("handball"),
                type(Sport.WATER_POLO) to listOf("waterpolo"),
                type(Sport.AUSTRALIAN_FOOTBALL) to listOf("australianfootball", "footballaustralian", "aussierules"),
                type(Sport.ROLLER_HOCKEY) to listOf("rollerhockey", "inlinehockey"),
                type(Sport.FLOORBALL) to listOf("floorball"),
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
                type(Sport.FLYING, SubSport.PARAGLIDING) to listOf("paragliding"),
                type(Sport.SKYDIVE) to listOf("skydiving", "jumpmaster"),
                type(Sport.BOWLING) to listOf("bowling"),
                type(Sport.PLAY) to listOf("play"),
                type(Sport.YARD_WORK) to listOf("yardwork", "gardening"),
                type(Sport.HOUSEWORK) to listOf("housework", "cleaning"),
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
            Sport.WALK to
                mapOf(
                    "indoorwalking" to type(Sport.WALK, SubSport.INDOOR),
                    "treadmill" to type(Sport.WALK, SubSport.TREADMILL),
                    "nordicwalking" to type(Sport.WALK, SubSport.NORDIC),
                ),
            Sport.CLIMB to
                mapOf("bouldering" to type(Sport.CLIMB, SubSport.BOULDERING), "indoorclimbing" to type(Sport.CLIMB, SubSport.INDOOR)),
            Sport.SKI to mapOf("backcountry" to type(Sport.SKI, SubSport.BACKCOUNTRY)),
            Sport.SNOWBOARD to mapOf("backcountry" to type(Sport.SNOWBOARD, SubSport.BACKCOUNTRY)),
            Sport.XC_SKI to
                mapOf(
                    "skateskiing" to type(Sport.XC_SKI, SubSport.SKATE),
                    "classicskiing" to type(Sport.XC_SKI, SubSport.CLASSIC),
                    "indoorskiing" to type(Sport.XC_SKI, SubSport.INDOOR),
                    "rollerskiing" to type(Sport.XC_SKI, SubSport.ROLLER),
                ),
            Sport.ROW to mapOf("indoorrowing" to type(Sport.ROW, SubSport.INDOOR), "virtualactivity" to type(Sport.ROW, SubSport.VIRTUAL)),
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
            "stretching" to type(Sport.MOBILITY, SubSport.STRETCHING),
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

    private fun List<DecodedSample>.toSamples(): List<LocationSample> = mapNotNull { r ->
        val lat = r.latitude ?: return@mapNotNull null
        val lon = r.longitude ?: return@mapNotNull null
        val millis = r.timestampMillis ?: return@mapNotNull null
        LocationSample(lat, lon, r.altitudeMeters, r.speedMps, accuracy = null, time = Instant.fromEpochMilliseconds(millis))
    }
}

/**
 * Single- or multi-gas by the gases breathed: Garmin's sub-sport is the watch's dive mode, so an air dive in its Multi-Gas mode would
 * otherwise import as multi-gas. Other dive types (CCR, gauge, apnea) keep the device's.
 */
private fun ActivityType.forGasesBreathed(dive: DecodedDive?): ActivityType {
    val gases = dive?.gases?.size ?: 0
    return when {
        subSport != SubSport.SINGLE_GAS && subSport != SubSport.MULTI_GAS -> this
        gases > 1 -> copy(subSport = SubSport.MULTI_GAS)
        gases == 1 -> copy(subSport = SubSport.SINGLE_GAS)
        else -> this
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

private const val CENTI = 100.0

/**
 * Mean depth while under water, each sample weighted by the time to the next one, so the surface time a computer logs before and after the
 * dive doesn't pull it toward zero. Null with nothing below the surface.
 */
private fun List<DepthPoint>.averageDepth(): Double? {
    var area = 0.0
    var seconds = 0L
    zipWithNext { a, b ->
        val dt = b.seconds - a.seconds
        if (dt > 0 && (a.depthMeters > 0 || b.depthMeters > 0)) {
            area += (a.depthMeters + b.depthMeters) / 2 * dt
            seconds += dt
        }
    }
    return if (seconds > 0) area / seconds else null
}

internal fun List<Double>.climb(): Double? {
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
internal inline fun List<DecodedSummary>.total(value: (DecodedSummary) -> Double?): Double? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

/** Average across sessions, weighted by timer time (falling back to equal weights). */
internal inline fun List<DecodedSummary>.weightedAverage(value: (DecodedSummary) -> Double?): Double? {
    val pairs = mapNotNull { s -> value(s)?.let { it to (s.totalTimerSeconds ?: 1.0) } }
    val weight = pairs.sumOf { it.second }
    return if (pairs.isEmpty() || weight <= 0) null else pairs.sumOf { it.first * it.second } / weight
}

private inline fun List<DecodedSample>.averageOf(value: (DecodedSample) -> Int?): Int? =
    mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private inline fun <T : Any> List<DecodedSample>.lastNotNullOf(value: (DecodedSample) -> T?): T? = asReversed().firstNotNullOfOrNull(value)

/** A FIT name in any SDK's spelling ("SINGLE_GAS_DIVING", "singleGasDiving") as one key: "singlegasdiving". */
internal fun String.fitKey() = filter { it.isLetterOrDigit() }.lowercase()
