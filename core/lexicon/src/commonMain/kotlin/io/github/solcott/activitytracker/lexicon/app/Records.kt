package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Known values of [ActivityRecord.sport]; the field is an open string. Tokens follow Garmin's activity profiles. */
object Sport {
    // Running, cycling, swimming, multisport
    const val RUN = "run"
    const val RIDE = "ride"
    const val HANDCYCLE = "handcycle"
    const val SWIM = "swim"
    const val MULTISPORT = "multisport"

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

    // Winter sports. SKI is alpine (downhill) skiing.
    const val SKI = "ski"
    const val SNOWBOARD = "snowboard"
    const val XC_SKI = "xcSki"
    const val SNOWSHOE = "snowshoe"
    const val ICE_SKATE = "iceSkate"

    // Water sports
    const val ROW = "row"
    const val KAYAK = "kayak"
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
    const val DANCE = "dance"
    const val GYMNASTICS = "gymnastics"

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
    @SerialName("\$type") val type: String = Nsid.FEED_ACTIVITY,
)

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
)

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
    val location: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    val weight: String? = null,
)
