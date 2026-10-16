package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
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
    val device: String? = null,
    /** A [SubSport] value refining [sport]; null for the sport's generic form. */
    val subSport: String? = null,
    /** Sensors paired with [device]: make and model only, never serial numbers or battery state. At most [ActivitySensor.MAX]. */
    val sensors: List<ActivitySensor>? = null,
    /** The dive computer's summary, for a dive. */
    val dive: ActivityDive? = null,
    /**
     * Another recording (a backup dive computer's, say) of the athlete's own activity at this URI, which isn't itself a recording. Feeds
     * show the activity once and list this among its [ActivityView.recordings].
     */
    val recordingOf: AtUri? = null,
    /** Photos, in the athlete's order. At most [ActivityMedia.MAX]. */
    val media: List<ActivityMedia>? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY,
)

/**
 * `app.activitytracker.feed.activityStreams`: an activity's per-sample data, for charts, under its activity's rkey. Its [data] blob holds
 * an [ActivityStreamData] as JSON.
 *
 * Where it's stored is who may see it: public streams in the athlete's repo, the others in their tier space ([AthleteSpace]), never wider
 * than the activity. The record carries no visibility of its own, and no zones: those are an [ActivityZonesRecord], which can be narrower.
 */
@Serializable
data class ActivityStreamsRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    /** [StreamChannel] values: what [data] holds. At most [MAX_CHANNELS]. */
    val channels: List<String>,
    val createdAt: Instant,
    /** Required by the lexicon: null only in the outbox, which uploads the blob and fills it in before `putRecord`. */
    val data: BlobRef? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_STREAMS,
) {
    companion object {
        const val MAX_CHANNELS = 16
        const val MIME_TYPE = "application/json"

        /** The lexicon's `maxSize` for [data]. */
        const val MAX_DATA_BYTES = 5_000_000L
    }
}

/**
 * `app.activitytracker.feed.activityTrack`: an activity's full-resolution GPS track as a GPX blob, under its activity's rkey. It holds
 * exact positions, so it's stored only in the athlete's [AthleteSpace.SELF] space, whatever the activity's visibility, and only the athlete
 * reads it: the AppView ignores it.
 */
@Serializable
data class ActivityTrackRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    val createdAt: Instant,
    /** Required by the lexicon: null only in the outbox, which uploads the blob and fills it in before `putRecord`. */
    val track: BlobRef? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_TRACK,
) {
    companion object {
        /** The record's blob field, for [track]. */
        const val TRACK_FIELD = "track"
    }
}

/**
 * `app.activitytracker.feed.activityZones`: the athlete's zones for an activity, under its activity's rkey, so later threshold changes
 * don't change its time in zone.
 *
 * Zone bounds reveal thresholds, so it's stored at the narrower of the activity's charts visibility and the athlete's training visibility:
 * in the repo when both are public, otherwise in their tier space ([AthleteSpace]). Where it's stored is its only visibility.
 */
@Serializable
data class ActivityZonesRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    val createdAt: Instant,
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_ZONES,
)

/** `app.activitytracker.feed.activityZones#zoneSnapshot`: the athlete's zones when the activity was published. */
@Immutable
@Serializable
data class ZoneSnapshot(
    /** A [HeartRateZoneModel] or [PowerZoneModel] value. */
    val model: String,
    /** Each zone's lower bound in bpm or watts, lowest zone first; each zone ends where the next begins. At most [MAX_ZONES]. */
    val lows: List<Int>,
) {
    companion object {
        const val MAX_ZONES = 12
    }
}

/**
 * `app.activitytracker.feed.activityStreams#streamData`: parallel arrays lined up with [time]. A channel the activity lacks is null, and a
 * missing sample within one is a null element. No positions: the activity's track holds those.
 */
@Immutable
@Serializable
data class ActivityStreamData(
    /** Seconds since the activity's `startedAt`. */
    val time: List<Int>,
    /** Centimeters from the start. */
    val distance: List<Int?>? = null,
    /** Centimeters above sea level. */
    val altitude: List<Int?>? = null,
    /** Millimeters per second. */
    val speed: List<Int?>? = null,
    /** Beats per minute. */
    val heartRate: List<Int?>? = null,
    /** Watts. */
    val power: List<Int?>? = null,
    /** Revolutions or steps per minute. */
    val cadence: List<Int?>? = null,
    /** Tenths of a degree Celsius. */
    val temperature: List<Int?>? = null,
    /** Centimeters below the surface. */
    val depth: List<Int?>? = null,
    val gears: List<GearChange>? = null,
) {
    /** The [StreamChannel]s this holds. */
    val channels: List<String>
        get() =
            listOfNotNull(
                StreamChannel.DISTANCE.takeIf { distance != null },
                StreamChannel.ALTITUDE.takeIf { altitude != null },
                StreamChannel.SPEED.takeIf { speed != null },
                StreamChannel.HEART_RATE.takeIf { heartRate != null },
                StreamChannel.POWER.takeIf { power != null },
                StreamChannel.CADENCE.takeIf { cadence != null },
                StreamChannel.TEMPERATURE.takeIf { temperature != null },
                StreamChannel.DEPTH.takeIf { depth != null },
                StreamChannel.GEARS.takeIf { gears != null },
            )
}

/** `app.activitytracker.feed.activityStreams#gearChange`: a shift, with the gears it shifted to (rear gear 1 is the largest cog). */
@Immutable
@Serializable
data class GearChange(
    /** Seconds since the activity's `startedAt`. */
    val time: Int,
    val front: Int? = null,
    val rear: Int? = null,
)

/** Known values of [ActivityStreamsRecord.channels], an open string. */
object StreamChannel {
    const val DISTANCE = "distance"
    const val ALTITUDE = "altitude"
    const val SPEED = "speed"
    const val HEART_RATE = "heartRate"
    const val POWER = "power"
    const val CADENCE = "cadence"
    const val TEMPERATURE = "temperature"
    const val DEPTH = "depth"
    const val GEARS = "gears"
}

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

/** `app.activitytracker.feed.activity#dive`: a dive's summary as the dive computer logged it. */
@Serializable
data class ActivityDive(
    /** Centimeters below the surface. */
    val maxDepth: Long? = null,
    /** Centimeters below the surface. */
    val avgDepth: Long? = null,
    /** The coldest water, in tenths of a degree Celsius; may be negative. */
    val minTemperature: Long? = null,
    /** Seconds from leaving the surface to the start of the final ascent. */
    val bottomTime: Long? = null,
    /** Seconds at the surface since the previous dive. */
    val surfaceInterval: Long? = null,
    /** The dive computer's own count. */
    val diveNumber: Int? = null,
    /** Breathed from a closed-circuit rebreather for at least part of the dive. */
    val closedCircuit: Boolean? = null,
    /** Each gas breathed, in order of first use. At most [MAX_GASES]. */
    val gases: List<ActivityDiveGas>? = null,
    /** Percent. */
    val gradientFactorLow: Int? = null,
    /** Percent. */
    val gradientFactorHigh: Int? = null,
    /** Central nervous system oxygen toxicity at the start, in percent of the limit. */
    val startCns: Int? = null,
    /** Central nervous system oxygen toxicity at the end, in percent of the limit. */
    val endCns: Int? = null,
    /** Whole-body oxygen exposure, in oxygen toxicity units. */
    val otu: Int? = null,
    /** Air consumption by tank pressure, adjusted to the surface, in millibar per minute. */
    val sac: Long? = null,
    /** Air consumption by volume (respiratory minute volume), in milliliters per minute. */
    val rmv: Long? = null,
    /** One per tank a pressure transmitter was on. At most [MAX_TANKS]. */
    val tanks: List<ActivityDiveTank>? = null,
    /** The dive site's name, never its coordinates: records are public. At most [MAX_SITE_LENGTH]. */
    val site: String? = null,
) {
    companion object {
        /** The lexicon's `maxLength`s. */
        const val MAX_GASES = 10
        const val MAX_TANKS = 8
        const val MAX_SITE_LENGTH = 640
    }
}

/** `app.activitytracker.feed.activity#diveGas`: whole percents; air is 21/0. */
@Serializable
data class ActivityDiveGas(val oxygen: Int, val helium: Int) {
    companion object {
        const val MAX_PERCENT = 100
    }
}

/** `app.activitytracker.feed.activity#diveTank`. */
@Serializable
data class ActivityDiveTank(
    /** Millibar. */
    val startPressure: Long? = null,
    /** Millibar. */
    val endPressure: Long? = null,
    /** Liters of gas used, at the surface. */
    val volumeUsed: Long? = null,
)

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
data class LikeRecord(
    val subject: StrongRef,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_LIKE,
)

@Serializable
data class CommentRecord(
    val subject: StrongRef,
    val text: String,
    val createdAt: Instant,
    /**
     * The [Visibility] the comment was written for: the activity's as the commenter's view showed it, narrowed for a reply to the audience
     * of the comment it answers ([Visibility.narrower]). Null means [Visibility.PUBLIC]. A comment is stored by its audience: in the repo
     * for public, else in the commenter's interactions space. The AppView never shows the comment wider, even if the activity is widened.
     */
    val audience: String? = null,
    /** Set on a reply; [subject] still names the activity. */
    val reply: ReplyRef? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_COMMENT,
)

/**
 * `app.activitytracker.feed.comment#replyRef`: threads are one level deep, so every reply is listed in [root]'s thread, however deep the
 * chain of replies it answers. [parent] is the comment answered: [root] itself or a reply in its thread.
 */
@Serializable data class ReplyRef(val root: StrongRef, val parent: StrongRef)

/**
 * `app.activitytracker.feed.commentHide`: the athlete hid the comment [subject] on one of their own activities. It's stored where their
 * like on that activity would be (the repo for a public one, else their interactions space), and its rkey is derived from [subject]
 * (`commentHideRkey` in `core/atproto`, which needs SHA-256), so there's at most one per comment and unhiding needs no lookup. Only the
 * activity's athlete's hide counts.
 */
@Serializable
data class CommentHideRecord(
    val subject: AtUri,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_COMMENT_HIDE,
)

@Serializable
data class FollowRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.GRAPH_FOLLOW,
)

/**
 * `app.activitytracker.graph.followApproval`: the athlete's approval of [subject]'s follow, which makes it count while their
 * [ProfileRecord.followPolicy] is [FollowPolicy.APPROVE]. Its rkey is always [subject]'s DID ([rkey]), so there's one per follower.
 */
@Serializable
data class FollowApprovalRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.GRAPH_FOLLOW_APPROVAL,
) {
    companion object {
        fun rkey(subject: Did): String = subject.value
    }
}

/**
 * `app.activitytracker.graph.followDecline`: the athlete declined [subject]'s follow request. It lives only in their [AthleteSpace.SELF]
 * space, so nobody else learns of it, and its rkey is [subject]'s DID ([rkey]).
 */
@Serializable
data class FollowDeclineRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.GRAPH_FOLLOW_DECLINE,
) {
    companion object {
        fun rkey(subject: Did): String = subject.value
    }
}

/** Known values of [ProfileRecord.followPolicy]; the field is an open string, and anything but [APPROVE] (or none) means [OPEN]. */
object FollowPolicy {
    /** Every follow counts at once. */
    const val OPEN = "open"

    /** A follow counts only once the athlete publishes a [FollowApprovalRecord] for the follower. */
    const val APPROVE = "approve"
}

/** `app.activitytracker.actor.profile`. Records are public, so [details] holds only the details the athlete made public. */
@Serializable
data class ProfileRecord(
    val displayName: String? = null,
    val description: String? = null,
    val avatar: BlobRef? = null,
    val details: ProfileDetails? = null,
    val units: String? = null,
    val sports: List<String> = emptyList(),
    /** A [FollowPolicy] value; null means [FollowPolicy.OPEN]. */
    val followPolicy: String? = null,
    val createdAt: Instant? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_PROFILE,
) {
    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.actor.privateDetails`: the details an athlete gave one visibility tier. It lives only in their
 * `app.activitytracker.space.athlete` space for that tier ([AthleteSpace.MUTUALS] or [AthleteSpace.SELF]), whose key is the details'
 * visibility, so it carries none of its own.
 */
@Serializable
data class PrivateDetailsRecord(
    val details: ProfileDetails,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_PRIVATE_DETAILS,
) {
    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.actor.activityDefaults`: the [Visibility] values an athlete's new activities and their charts start with. It lives
 * only in their [AthleteSpace.SELF] space. A missing record or field means [Visibility.PUBLIC]; read it through [resolved], which also caps
 * the charts at the activity.
 */
@Serializable
data class ActivityDefaultsRecord(
    val visibility: String? = null,
    val chartsVisibility: String? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_ACTIVITY_DEFAULTS,
) {
    /** (activity, charts), each a known [Visibility] value, with the charts never wider than the activity. */
    fun resolved(): Pair<String, String> {
        val activity = Visibility.known(visibility)
        return activity to Visibility.capped(Visibility.known(chartsVisibility), activity)
    }

    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.actor.privacyZones`: the places an athlete hides from their routes, and how much of each route's start and end. It
 * lives only in their [AthleteSpace.SELF] space. Every activity's polyline is trimmed by it before it's written, at every visibility.
 */
@Serializable
data class PrivacyZonesRecord(
    /** A new TID on every save ([RouteTrimRecord.revision] names the one polylines were trimmed by). */
    val revision: String,
    val zones: List<PrivacyZone>? = null,
    /** Meters hidden from the start and from the end of every route; null or 0 is off. */
    val hideEndsMeters: Int? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_PRIVACY_ZONES,
) {
    companion object {
        const val RKEY = "self"
        const val MAX_ZONES = 10
        /** A zone's smallest radius. [hideEndsMeters] starts at 0 (off). */
        const val MIN_RADIUS_METERS = 200
        /** The largest radius and hidden start/end. */
        const val MAX_METERS = 1600
        /** The step radii and hidden start/end are chosen in. */
        const val STEP_METERS = 200
    }
}

/** `app.activitytracker.actor.privacyZones#zone`: a circle hidden from routes, its center already offset from the place chosen. */
@Immutable
@Serializable
data class PrivacyZone(
    /** Center latitude in 1e-7 degrees. */
    val latitudeE7: Int,
    /** Center longitude in 1e-7 degrees. */
    val longitudeE7: Int,
    /** Meters, [PrivacyZonesRecord.MIN_RADIUS_METERS] to [PrivacyZonesRecord.MAX_METERS]. */
    val radiusMeters: Int,
    val label: String? = null,
) {
    companion object {
        const val MAX_LABEL_LENGTH = 32
    }
}

/**
 * `app.activitytracker.actor.routeTrim`: the [PrivacyZonesRecord.revision] every one of the athlete's polylines has been trimmed by and
 * sent. It lives only in their [AthleteSpace.SELF] space, apart from the zones so finishing a re-trim never overwrites a zone saved since.
 */
@Serializable
data class RouteTrimRecord(
    val revision: String,
    val updatedAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_ROUTE_TRIM,
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

/**
 * `app.activitytracker.actor.defs#details`. Who sees each one is where it's stored: the public [ProfileRecord], or the athlete's
 * [AthleteSpace] tier space holding a [PrivateDetailsRecord].
 */
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
    /** Millimeters (lexicons have no floats). Always stored with [weight], under one visibility. */
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
    /**
     * Every value the thresholds (the defaults' and each sport's) have had. The newest for a threshold is its value unless since cleared.
     */
    val history: List<ThresholdEntry> = emptyList(),
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

        /** The lexicon's `maxLength` of [history]. */
        const val MAX_HISTORY = 500

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

/**
 * `app.activitytracker.actor.defs#thresholdEntry`: a value a threshold took, when it took effect and where it came from. [sport] is the
 * override that took it, or null for the defaults.
 */
@Immutable
@Serializable
data class ThresholdEntry(
    /** A [Threshold] value. */
    val threshold: String,
    /** In the threshold's unit: watts, beats per minute, or millimeters per second for [Threshold.THRESHOLD_SPEED]. */
    val value: Long,
    /** When it took effect; null when unknown, for a value saved before history was kept. */
    val setAt: Instant? = null,
    /** A [Sport] value, or null for the defaults. */
    val sport: String? = null,
    /** A [ThresholdSource] value, or null when unknown. */
    val source: String? = null,
    /** The recording device, for a value from [ThresholdSource.DEVICE]. */
    val device: String? = null,
) {
    companion object {
        /** The lexicon's `maximum` of [value]. */
        const val MAX_VALUE = 10_000L
    }
}

/** Known values of [TrainingDetails.autoUpdate] and [ThresholdEntry.threshold]: the thresholds, named as their fields. */
object Threshold {
    const val FUNCTIONAL_THRESHOLD_POWER = "functionalThresholdPower"
    const val MAX_HEART_RATE = "maxHeartRate"
    const val THRESHOLD_HEART_RATE = "thresholdHeartRate"
    const val RESTING_HEART_RATE = "restingHeartRate"

    /** Per sport only, and never read from a device, so it isn't in [all]. */
    const val THRESHOLD_SPEED = "thresholdSpeed"

    /** The thresholds the defaults have, which imports may update. */
    val all = listOf(FUNCTIONAL_THRESHOLD_POWER, MAX_HEART_RATE, THRESHOLD_HEART_RATE, RESTING_HEART_RATE)
}

/** Known values of [ThresholdEntry.source]; the field is an open string. */
object ThresholdSource {
    /** Entered by the athlete. */
    const val MANUAL = "manual"

    /** Read from the recording device's settings in an imported activity. */
    const val DEVICE = "device"
    const val GARMIN = "garmin"
    const val TRAINER_ROAD = "trainerRoad"
    const val TRAINING_PEAKS = "trainingPeaks"
    const val INTERVALS_ICU = "intervalsIcu"
    const val ZWIFT = "zwift"
    const val WAHOO = "wahoo"
    const val STRAVA = "strava"
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

    /** The athlete's effective followers: every follower, or only approved ones when their policy is [FollowPolicy.APPROVE]. */
    const val FOLLOWERS = "followers"

    /** Accounts the athlete follows that also follow the athlete. */
    const val MUTUALS = "mutuals"

    /** Only the athlete. */
    const val SELF = "self"

    /** Every known value, widest first. */
    val all = listOf(PUBLIC, FOLLOWERS, MUTUALS, SELF)

    /** [value]'s place in [all], 0 the widest. An unknown value ranks as [SELF], so it is never shown wider than intended. */
    fun rank(value: String): Int = all.indexOf(value).takeIf { it >= 0 } ?: all.lastIndex

    /** The narrower of [a] and [b]. */
    fun narrower(a: String, b: String): String = if (rank(a) >= rank(b)) a else b

    /** [value], narrowed to [cap] if it's wider: charts are never more visible than their activity. */
    fun capped(value: String, cap: String): String = narrower(value, cap)

    /** The values no wider than [cap], widest first: what a charts picker offers for an activity at [cap]. */
    fun atMost(cap: String): List<String> = all.drop(rank(cap))

    /** [value] if it's a known value, [PUBLIC] when it's absent, else [SELF]. */
    fun known(value: String?): String =
        when (value) {
            null -> PUBLIC
            in all -> value
            else -> SELF
        }
}
