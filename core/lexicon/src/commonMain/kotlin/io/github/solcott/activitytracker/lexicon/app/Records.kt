package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Known values of [ActivityRecord.sport]; the field is an open string. Tokens follow Garmin's activity profiles, plus what Apple Health,
 * Health Connect and Strava track that Garmin doesn't.
 */
object Sport {
    // Running, cycling, swimming, multisport
    const val RUN = "run"
    const val RIDE = "ride"
    const val HANDCYCLE = "handcycle"
    const val SWIM = "swim"
    const val MULTISPORT = "multisport"
    const val WATER_FITNESS = "waterFitness"

    // Outdoor
    const val WALK = "walk"
    const val HIKE = "hike"
    const val RUCKING = "rucking"
    const val MOUNTAINEERING = "mountaineering"
    const val CLIMB = "climb"
    const val INLINE_SKATE = "inlineSkate"
    const val HORSEBACK = "horseback"
    const val GOLF = "golf"
    const val DISC_GOLF = "discGolf"
    const val ARCHERY = "archery"
    const val HUNT = "hunt"
    const val FISH = "fish"
    const val ADVENTURE_RACE = "adventureRace"
    const val EXPEDITION = "expedition"
    const val WHEELCHAIR = "wheelchair"
    const val SKATEBOARD = "skateboard"
    const val KICK_SCOOTER = "kickScooter"
    const val ORIENTEERING = "orienteering"
    const val PARKOUR = "parkour"

    // Winter sports. SKI is alpine (downhill) skiing.
    const val SKI = "ski"
    const val SNOWBOARD = "snowboard"
    const val XC_SKI = "xcSki"
    const val SNOWSHOE = "snowshoe"
    const val ICE_SKATE = "iceSkate"
    const val CURLING = "curling"

    // Water sports
    const val ROW = "row"
    const val KAYAK = "kayak"
    const val CANOE = "canoe"
    const val SUP = "sup"
    const val SURF = "surf"
    const val KITEBOARD = "kiteboard"
    const val WINDSURF = "windsurf"
    const val SAIL = "sail"
    const val WAKEBOARD = "wakeboard"
    const val WAKESURF = "wakesurf"
    const val WATER_SKI = "waterSki"
    const val WHITEWATER = "whitewater"
    const val TUBE = "tube"
    const val SNORKEL = "snorkel"
    const val BOAT = "boat"

    // Diving
    const val DIVE = "dive"

    // Gym
    const val STRENGTH = "strength"
    const val CARDIO = "cardio"
    const val HIIT = "hiit"
    const val YOGA = "yoga"
    const val PILATES = "pilates"
    const val MOBILITY = "mobility"
    const val ELLIPTICAL = "elliptical"
    const val STAIR_STEPPER = "stairStepper"
    const val FLOOR_CLIMB = "floorClimb"
    const val JUMP_ROPE = "jumpRope"
    const val BOXING = "boxing"
    const val MMA = "mma"
    const val WRESTLING = "wrestling"
    const val FENCING = "fencing"
    const val TAI_CHI = "taiChi"
    const val DANCE = "dance"
    const val GYMNASTICS = "gymnastics"
    const val RECOVERY = "recovery"

    // Team sports
    const val SOCCER = "soccer"
    const val AMERICAN_FOOTBALL = "americanFootball"
    const val BASEBALL = "baseball"
    const val SOFTBALL = "softball"
    const val BASKETBALL = "basketball"
    const val VOLLEYBALL = "volleyball"
    const val ICE_HOCKEY = "iceHockey"
    const val FIELD_HOCKEY = "fieldHockey"
    const val LACROSSE = "lacrosse"
    const val RUGBY = "rugby"
    const val CRICKET = "cricket"
    const val ULTIMATE = "ultimate"
    const val HANDBALL = "handball"
    const val WATER_POLO = "waterPolo"
    const val AUSTRALIAN_FOOTBALL = "australianFootball"
    const val ROLLER_HOCKEY = "rollerHockey"
    const val FLOORBALL = "floorball"

    // Racket sports
    const val TENNIS = "tennis"
    const val PICKLEBALL = "pickleball"
    const val PADEL = "padel"
    const val BADMINTON = "badminton"
    const val SQUASH = "squash"
    const val RACQUETBALL = "racquetball"
    const val TABLE_TENNIS = "tableTennis"
    const val PLATFORM_TENNIS = "platformTennis"

    // Motorsports
    const val MOTORCYCLE = "motorcycle"
    const val MOTOCROSS = "motocross"
    const val ATV = "atv"
    const val SNOWMOBILE = "snowmobile"
    const val OVERLAND = "overland"

    // Other
    const val MEDITATION = "meditation"
    const val BREATHWORK = "breathwork"
    const val FLYING = "flying"
    const val SKYDIVE = "skydive"
    const val GAMING = "gaming"
    const val BOWLING = "bowling"
    const val PLAY = "play"
    const val YARD_WORK = "yardWork"
    const val HOUSEWORK = "housework"
    const val OTHER = "other"
}

/**
 * Known values of [ActivityRecord.subSport], an open string that refines [ActivityRecord.sport] and means nothing without it. One token can
 * refine several sports (`indoor` rides, walks, rows…).
 */
object SubSport {
    const val TRAIL = "trail"
    const val TREADMILL = "treadmill"
    const val TRACK = "track"
    const val INDOOR_TRACK = "indoorTrack"
    const val VIRTUAL = "virtual"
    const val ULTRA = "ultra"
    const val OBSTACLE = "obstacle"

    const val ROAD = "road"
    const val MOUNTAIN = "mountain"
    const val GRAVEL = "gravel"
    const val CYCLOCROSS = "cyclocross"
    const val INDOOR = "indoor"
    const val E_BIKE = "eBike"
    const val E_MOUNTAIN = "eMountain"
    const val COMMUTE = "commute"
    const val TOUR = "tour"
    const val BMX = "bmx"

    const val POOL = "pool"
    const val OPEN_WATER = "openWater"

    const val TRIATHLON = "triathlon"
    const val DUATHLON = "duathlon"
    const val SWIMRUN = "swimrun"
    const val BRICK = "brick"
    const val MIXED = "mixed"

    const val BOULDERING = "bouldering"
    const val BACKCOUNTRY = "backcountry"
    const val CLASSIC = "classic"
    const val SKATE = "skate"
    const val RACE = "race"
    const val EXPEDITION = "expedition"

    const val SINGLE_GAS = "singleGas"
    const val MULTI_GAS = "multiGas"
    const val CCR = "ccr"
    const val GAUGE = "gauge"
    const val APNEA = "apnea"
    const val APNEA_HUNT = "apneaHunt"
    const val POOL_APNEA = "poolApnea"

    const val WALK_PACE = "walkPace"
    const val RUN_PACE = "runPace"
    const val NORDIC = "nordic"
    const val ROLLER = "roller"

    const val FUNCTIONAL = "functional"
    const val CROSSFIT = "crossfit"
    const val CALISTHENICS = "calisthenics"
    const val BOOTCAMP = "bootcamp"
    const val CIRCUIT = "circuit"
    const val CARDIO = "cardio"
    const val SOCIAL = "social"

    const val PARAGLIDING = "paragliding"

    const val VELOMOBILE = "velomobile"
    const val DOG = "dog"
    const val STROLLER = "stroller"
    const val FUTSAL = "futsal"
    const val BEACH = "beach"
    const val KICKBOXING = "kickboxing"
    const val BARRE = "barre"
    const val STEP = "step"
    const val CORE = "core"
    const val STRETCHING = "stretching"
    const val COOLDOWN = "cooldown"
    const val SAUNA = "sauna"
    const val COLD_PLUNGE = "coldPlunge"
}

/** `app.activitytracker.feed.activity`. Units are integers because lexicons have no floats. */
@Immutable
@Serializable
data class ActivityRecord(
    /** A [Sport] value. */
    val sport: String,
    val startedAt: Instant,
    /** Seconds. */
    val elapsedTime: Long,
    val createdAt: Instant,
    val title: String? = null,
    val description: String? = null,
    /** Seconds. */
    val movingTime: Long? = null,
    /** Meters. */
    val distance: Long? = null,
    /** Meters. */
    val elevationGain: Long? = null,
    /** Millimeters per second. */
    val avgSpeed: Long? = null,
    /** Millimeters per second. */
    val maxSpeed: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Tenths of a degree Celsius; may be negative. */
    val avgTemperature: Long? = null,
    /** Simplified Google encoded polyline (precision 5). */
    val polyline: String? = null,
    /** Full GPX track blob. */
    val track: BlobRef? = null,
    val device: String? = null,
    /** A [SubSport] value refining [sport]; null for the sport's generic form. */
    val subSport: String? = null,
    /** Sensors paired with [device]: make and model only, never serial numbers or battery state. At most [ActivitySensor.MAX]. */
    val sensors: List<ActivitySensor>? = null,
    @SerialName("\$type") val type: String = Nsid.FEED_ACTIVITY,
)

/** `app.activitytracker.feed.activity#sensor`: a sensor paired with the recording device. */
@Serializable
data class ActivitySensor(
    /** A [SensorType] value. */
    val kind: String,
    /** "Garmin". */
    val manufacturer: String? = null,
    /** "HRM 200"; null when only its maker can name it. */
    val model: String? = null,
) {
    companion object {
        /** The lexicon's `maxLength` for an activity's sensors. */
        const val MAX = 16
    }
}

/** Known values of [ActivitySensor.kind], an open string. */
object SensorType {
    const val HEART_RATE = "heartRate"
    const val POWER = "power"
    const val SPEED_CADENCE = "speedCadence"
    const val SPEED = "speed"
    const val CADENCE = "cadence"
    const val SHIFTING = "shifting"
    const val RADAR = "radar"
    const val LIGHT = "light"
    const val TRAINER = "trainer"
    const val FOOTPOD = "footpod"
    const val OTHER = "other"
}

@Serializable
data class KudosRecord(
    val subject: StrongRef,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.FEED_KUDOS,
)

@Serializable
data class CommentRecord(
    val subject: StrongRef,
    val text: String,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.FEED_COMMENT,
)

@Serializable
data class FollowRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.GRAPH_FOLLOW,
)

/** `app.activitytracker.actor.profile`. Records are public, so [details] holds only the details the athlete made public. */
@Serializable
data class ProfileRecord(
    val displayName: String? = null,
    val description: String? = null,
    val avatar: BlobRef? = null,
    val details: ProfileDetails? = null,
    val units: String? = null,
    val sports: List<String> = emptyList(),
    val createdAt: Instant? = null,
    @SerialName("\$type") val type: String = Nsid.ACTOR_PROFILE,
) {
    companion object {
        const val RKEY = "self"
    }
}

/** `app.activitytracker.actor.defs#location` */
@Immutable
@Serializable
data class Location(
    /** City or town. */
    val locality: String? = null,
    /** State, province or similar. */
    val region: String? = null,
    /** ISO 3166-1 alpha-2 code, upper case. */
    val country: String? = null,
)

/** `app.activitytracker.actor.defs#details`. Who sees each one is set by a [DetailsVisibility]. */
@Immutable
@Serializable
data class ProfileDetails(
    val location: Location? = null,
    /** Calendar date as `YYYY-MM-DD`. */
    val birthDate: String? = null,
    /** A [Gender] value, or the athlete's own description. */
    val gender: String? = null,
    /** Grams (lexicons have no floats). */
    val weight: Long? = null,
    /** Millimeters (lexicons have no floats). Shares [DetailsVisibility.weight] with [weight]. */
    val height: Long? = null,
    val training: TrainingDetails? = null,
) {
    companion object {
        /** The lexicon's maximum [weight]. */
        const val MAX_WEIGHT_GRAMS = 500_000L

        /** The lexicon's maximum [height]. */
        const val MAX_HEIGHT_MM = 3_000L
    }
}

/** `app.activitytracker.actor.defs#training`: the athlete's thresholds, and the models their zones are derived from. */
@Immutable
@Serializable
data class TrainingDetails(
    /** Cycling FTP in watts. */
    val functionalThresholdPower: Long? = null,
    /** Beats per minute. */
    val maxHeartRate: Long? = null,
    /** Lactate threshold heart rate, beats per minute. */
    val thresholdHeartRate: Long? = null,
    /** Beats per minute. */
    val restingHeartRate: Long? = null,
    /** A [HeartRateZoneModel] value. */
    val heartRateZoneModel: String? = null,
    /** A [PowerZoneModel] value. */
    val powerZoneModel: String? = null,
    /** [Threshold] values that imported activities may update from the recording device's settings. */
    val autoUpdate: List<String> = emptyList(),
    /** When each threshold took effect. */
    val setAt: TrainingSetAt? = null,
    /** Per-sport overrides of the values above, at most one per sport. */
    val sports: List<SportTraining> = emptyList(),
) {
    companion object {
        const val MAX_FTP = 2500L
        const val MIN_HEART_RATE = 20L
        const val MAX_HEART_RATE = 250L
        const val MAX_MODEL_LENGTH = 64

        /** The lexicon's `maxLength` of [autoUpdate]. */
        const val MAX_AUTO_UPDATE = 4

        /** The lexicon's `maxLength` of [sports]. */
        const val MAX_SPORTS = 16

        /** Millimeters per second. */
        const val MIN_THRESHOLD_SPEED = 100L

        /** Millimeters per second. */
        const val MAX_THRESHOLD_SPEED = 10_000L
    }
}

/**
 * `app.activitytracker.actor.defs#sportTraining`: thresholds and zone models for one sport, overriding [TrainingDetails]' defaults where
 * set. Resting heart rate isn't per sport.
 */
@Immutable
@Serializable
data class SportTraining(
    /** A [Sport] value. */
    val sport: String,
    /** Threshold power for this sport, watts. */
    val functionalThresholdPower: Long? = null,
    /** Beats per minute. */
    val maxHeartRate: Long? = null,
    /** Lactate threshold heart rate, beats per minute. */
    val thresholdHeartRate: Long? = null,
    /** Threshold speed, millimeters per second (lexicons have no floats). */
    val thresholdSpeed: Long? = null,
    /** A [HeartRateZoneModel] value. */
    val heartRateZoneModel: String? = null,
    /** A [PowerZoneModel] value. */
    val powerZoneModel: String? = null,
    /** A [SpeedZoneModel] value. */
    val speedZoneModel: String? = null,
    /** When each of this sport's thresholds took effect. */
    val setAt: TrainingSetAt? = null,
)

/**
 * `app.activitytracker.actor.defs#trainingSetAt`: when each threshold took effect, set by the athlete or read from an activity that started
 * then. An import only updates a threshold from an activity that started later.
 */
@Immutable
@Serializable
data class TrainingSetAt(
    val functionalThresholdPower: Instant? = null,
    val maxHeartRate: Instant? = null,
    val thresholdHeartRate: Instant? = null,
    val restingHeartRate: Instant? = null,
    val thresholdSpeed: Instant? = null,
)

/** Known values of [TrainingDetails.autoUpdate]: the thresholds, named as their fields. */
object Threshold {
    const val FUNCTIONAL_THRESHOLD_POWER = "functionalThresholdPower"
    const val MAX_HEART_RATE = "maxHeartRate"
    const val THRESHOLD_HEART_RATE = "thresholdHeartRate"
    const val RESTING_HEART_RATE = "restingHeartRate"

    val all = listOf(FUNCTIONAL_THRESHOLD_POWER, MAX_HEART_RATE, THRESHOLD_HEART_RATE, RESTING_HEART_RATE)
}

/** Known values of [TrainingDetails.heartRateZoneModel]; the field is an open string. */
object HeartRateZoneModel {
    const val FRIEL_RUNNING = "frielRunning"
    const val FRIEL_CYCLING = "frielCycling"
    const val COGGAN = "cogganHeartRate"
    const val PERCENT_MAX = "percentMaxHeartRate"
    const val RESERVE = "heartRateReserve"
}

/** Known values of [TrainingDetails.powerZoneModel]; the field is an open string. */
object PowerZoneModel {
    const val COGGAN_6 = "coggan6"
    const val COGGAN_7 = "coggan7"
}

/** Known values of [SportTraining.speedZoneModel]; the field is an open string. */
object SpeedZoneModel {
    const val FRIEL_RUNNING = "frielRunning"
    const val FRIEL_SWIMMING = "frielSwimming"
}

/** Known values of [ProfileDetails.gender]; the field is an open string. */
object Gender {
    const val FEMALE = "female"
    const val MALE = "male"
    const val NON_BINARY = "nonBinary"

    val all = listOf(FEMALE, MALE, NON_BINARY)
}

/** Known values of `app.activitytracker.actor.defs#visibility`; the field is an open string. */
object Visibility {
    /** Anyone, so the detail goes in the public profile record. */
    const val PUBLIC = "public"

    /** Accounts the athlete follows that also follow the athlete. */
    const val MUTUALS = "mutuals"

    /** Only the athlete. */
    const val SELF = "self"
}

/** `app.activitytracker.actor.defs#detailsVisibility`: a [Visibility] value per detail group. */
@Immutable
@Serializable
data class DetailsVisibility(
    /** The whole [Location]: locality, region and country. */
    val location: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    /** [ProfileDetails.weight] and [ProfileDetails.height] together. */
    val weight: String? = null,
    val training: String? = null,
)
