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
    /** Meters. */
    val elevationLoss: Long? = null,
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
    /**
     * Indices of [polyline]'s decoded points after which its line breaks, ascending: where no route was recorded, between a [combined]
     * activity's back-to-back members or where a single recording lost its position.
     */
    val polylineGaps: List<Int>? = null,
    val device: String? = null,
    /** A [SubSport] value refining [sport]; null for the sport's generic form. */
    val subSport: String? = null,
    /** Sensors paired with [device]: make and model only, never serial numbers or battery state. At most [ActivitySensor.MAX]. */
    val sensors: List<ActivitySensor>? = null,
    /** The dive computer's summary, for a dive. */
    val dive: ActivityDive? = null,
    /**
     * Legacy: another recording (a backup dive computer's, say) of the athlete's own activity at this URI, which isn't itself a recording.
     * Feeds show the activity once and list this among its [ActivityView.recordings]. Clients no longer write it: other recordings are
     * combined ([combined]), and `LegacyGroupMigration` turns existing groups into combined activities.
     */
    val recordingOf: AtUri? = null,
    /** Photos, in the athlete's order. At most [ActivityMedia.MAX]. */
    val media: List<ActivityMedia>? = null,
    /** Set when this activity combines several of the athlete's own recordings of one activity, which feeds then show only as this. */
    val combined: CombinedActivity? = null,
    /** Revolutions or steps per minute. */
    val avgCadence: Int? = null,
    /** Revolutions or steps per minute. */
    val maxCadence: Int? = null,
    /** Watts. */
    val avgPower: Int? = null,
    /** Watts. */
    val maxPower: Int? = null,
    /** Watts. */
    val normalizedPower: Int? = null,
    /** Kilojoules. */
    val work: Long? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    /** Front derailleur shifts by electronic shifting. */
    val frontShifts: Int? = null,
    /** Rear derailleur shifts by electronic shifting. */
    val rearShifts: Int? = null,
    /** Mountain bike jumps. */
    val jumps: Int? = null,
    /** Garmin's mountain bike grit (trail difficulty), in tenths. */
    val grit: Int? = null,
    /** Garmin's mountain bike flow (smoothness), in tenths. */
    val flow: Int? = null,
    /** A pool swim's pool length, in centimeters. */
    val poolLength: Int? = null,
    /**
     * The recording device's cycle count: steps on foot (a run's strides doubled), strokes swimming, rowing or paddling, pushes in a
     * wheelchair, crank revolutions on a ride.
     */
    val cycles: Int? = null,
    /** Average distance per stroke, in centimeters. */
    val avgStrokeDistance: Int? = null,
    /** The UTC offset where the activity started, in minutes east of UTC, from [MIN_UTC_OFFSET] to [MAX_UTC_OFFSET]. */
    val utcOffset: Int? = null,
    /**
     * The athlete's gear used, by the canonical URIs of their [GearItemRecord]s, in the order they chose; at most [MAX_GEAR]. A URI of
     * anyone else's item is ignored. Null when the activity lists no gear. In [DetailCategory.GEAR].
     */
    val gear: List<AtUri>? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY,
) {
    companion object {
        /** The lexicon's `maxLength` for [gear]. */
        const val MAX_GEAR = 16

        /** Minutes east of UTC: UTC−12:00 to UTC+14:00. */
        const val MIN_UTC_OFFSET = -720
        const val MAX_UTC_OFFSET = 840
    }
}

/**
 * `app.activitytracker.feed.activity#combined`: the athlete's own recordings an activity combines, at the same time on different devices or
 * one after another, and for each channel which of them it's taken from. Members are never rewritten; the combined activity's figures,
 * streams and track are built from theirs.
 */
@Immutable
@Serializable
data class CombinedActivity(
    /** [MIN_MEMBERS] to [MAX_MEMBERS] of the athlete's activities. */
    val members: List<AtUri>,
    /** Per channel, the members it comes from, best first. */
    val sources: List<ChannelSource> = emptyList(),
) {
    /** The ranking for [channel], or null when none is stored. */
    fun sourcesOf(channel: String): List<AtUri>? = sources.firstOrNull { it.channel == channel }?.members

    companion object {
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 10
    }
}

/**
 * `app.activitytracker.feed.activity#channelSource`: [members] of a combined activity that [channel] comes from, best first; none when the
 * athlete left it out.
 */
@Immutable @Serializable data class ChannelSource(val channel: String, val members: List<AtUri>)

/**
 * [ChannelSource.channel] values beyond the stream channels (`feed.activityStreams#streamData`'s names, such as `heartRate`), an open set:
 * the data a combined activity takes whole from one member rather than sample by sample.
 */
object CombinedChannel {
    /** The track and polyline. */
    const val POSITION = "position"
    const val LAPS = "laps"
    const val LEGS = "legs"
    const val SETS = "sets"
    const val LENGTHS = "lengths"
    /** The dive summary, alerts, gas switches and tank pressures. */
    const val DIVE = "dive"
}

/**
 * `app.activitytracker.feed.activityDetails`: an activity's summary fields whose [DetailCategory] is narrower than the activity, under its
 * activity's rkey. The fields have the same names and units as on [ActivityRecord], which leaves them out.
 *
 * It's stored only in the athlete's tier space ([AthleteSpace]) for those categories' visibility, at most one per tier, and never in the
 * repo (the AppView ignores a repo copy). Where it's stored is its only visibility.
 */
@Serializable
data class ActivityDetailsRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    val createdAt: Instant,
    /** The exact start, when the activity's own `startedAt` is only the start of its local day. */
    val startedAt: Instant? = null,
    /** The trimmed polyline, when the map is narrower than the activity. */
    val polyline: String? = null,
    /** [polyline]'s breaks ([ActivityRecord.polylineGaps]), with it. */
    val polylineGaps: List<Int>? = null,
    /** Millimeters per second. */
    val avgSpeed: Long? = null,
    /** Millimeters per second. */
    val maxSpeed: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Revolutions or steps per minute. */
    val avgCadence: Int? = null,
    /** Revolutions or steps per minute. */
    val maxCadence: Int? = null,
    /** Watts. */
    val avgPower: Int? = null,
    /** Watts. */
    val maxPower: Int? = null,
    /** Watts. */
    val normalizedPower: Int? = null,
    /** Kilojoules. */
    val work: Long? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    /** [ActivityRecord.gear], when the gear category is narrower than the activity. */
    val gear: List<AtUri>? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_DETAILS,
)

/**
 * `app.activitytracker.feed.activityStreams`: an activity's per-sample data, for charts, under its activity's rkey. Its [data] blob holds
 * an [ActivityStreamData] as JSON.
 *
 * An activity has one per tier that holds any of its channels ([DetailCategory.streamsFor]). Where each is stored is who may see it: public
 * streams in the athlete's repo, the others in their tier space ([AthleteSpace]), never wider than the activity. The record carries no
 * visibility of its own, and no zones: those are an [ActivityZonesRecord], which can be narrower.
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
        const val MAX_CHANNELS = 64
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
 * `app.activitytracker.feed.segmentTrack`: an activity's track trimmed by the athlete's privacy zones, as a [SegmentTrackData] JSON blob
 * under its activity's rkey, for segment matching. Written only while [SegmentPrefsRecord.matching] is on, and stored only in the athlete's
 * [AthleteSpace.SELF] space; the AppView indexes it from there, uses it only while matching is on, and never serves it.
 */
@Serializable
data class SegmentTrackRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    val createdAt: Instant,
    /** The [PrivacyZonesRecord.revision] it was trimmed by; null when the zones were never saved. */
    val zonesRevision: String? = null,
    /** Required by the lexicon: null only in the outbox, which uploads the blob and fills it in before `putRecord`. */
    val data: BlobRef? = null,
    /** The activity's efforts are ranked on no leaderboard; null means false. Kept when the track is rewritten for the same activity. */
    val excludeFromLeaderboards: Boolean? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_SEGMENT_TRACK,
) {
    companion object {
        /** The record's blob field, for [data]. */
        const val DATA_FIELD = "data"
        const val MIME_TYPE = "application/json"
    }
}

/** `app.activitytracker.feed.segmentTrack#trackData`: the shown stretches of a track, in order, never joined to each other. */
@Serializable data class SegmentTrackData(val pieces: List<SegmentTrackPiece>)

/** `app.activitytracker.feed.segmentTrack#piece`: one unbroken stretch; every array lines up with [t]. */
@Serializable
data class SegmentTrackPiece(
    /** Latitudes in 1e-7 degrees. */
    val lat: List<Int>,
    /** Longitudes in 1e-7 degrees. */
    val lng: List<Int>,
    /** Seconds since the first point of the activity's full track. */
    val t: List<Long>,
    /** Altitudes in centimeters, a missing sample null; null when the track has no altitude. */
    val alt: List<Int?>? = null,
)

/** Known values of [SegmentRecord.sport]: the sport groups an activity can match a segment in ([SegmentSport]). An open string. */
object SegmentSportGroup {
    const val RIDE = "ride"
    const val RUN = "run"
    const val HIKE = "hike"
    const val SKI = "ski"
    const val PADDLE = "paddle"

    val all = listOf(RIDE, RUN, HIKE, SKI, PADDLE)

    /** The longest a segment of any group may be along its line, in meters. */
    const val MAX_METERS = 50_000

    private const val MIN_RIDE_METERS = 500
    private const val MIN_OTHER_METERS = 200

    /** The shortest a segment of [group] may be along its line, in meters; rides are longer so a short sprint isn't a segment. */
    fun minMeters(group: String): Int = if (group == RIDE) MIN_RIDE_METERS else MIN_OTHER_METERS

    /**
     * The fastest average speed over a segment of [group] that leaderboards rank, in mm/s (segments/segment-leaderboards): 100 km/h for
     * rides, 40 for runs, 30 for hikes, 120 for skiing and 25 for paddling. A faster effort stays on its activity but off every board.
     */
    fun maxSpeedMmPerS(group: String): Int =
        when (group) {
            RIDE -> MAX_RIDE_MM_PER_S
            RUN -> MAX_RUN_MM_PER_S
            HIKE -> MAX_HIKE_MM_PER_S
            SKI -> MAX_SKI_MM_PER_S
            else -> MAX_PADDLE_MM_PER_S
        }

    private const val MAX_RIDE_MM_PER_S = 27_778
    private const val MAX_RUN_MM_PER_S = 11_111
    private const val MAX_HIKE_MM_PER_S = 8_333
    private const val MAX_SKI_MM_PER_S = 33_333
    private const val MAX_PADDLE_MM_PER_S = 6_944
}

/**
 * Known values of a segment view's `status`, which the AppView derives (segments/segment-records); an open string, where null or an unknown
 * value means [ACTIVE].
 */
object SegmentStatus {
    /** New, and not yet ridden by enough athletes. */
    const val PROPOSED = "proposed"
    const val ACTIVE = "active"
    /** Duplicates an earlier segment, named by `duplicateOf`; never matched. */
    const val DUPLICATE = "duplicate"
    /** Still proposed long after it was created; keeps its efforts but gets no new ones. */
    const val ARCHIVED = "archived"

    private val NOT_ACTIVE = setOf(PROPOSED, DUPLICATE, ARCHIVED)

    /** [status] as shown, with null or an unknown value as [ACTIVE]. */
    fun of(status: String?): String = status?.takeIf { it in NOT_ACTIVE } ?: ACTIVE
}

/** Known values of [SegmentRecord.source]; an open string. */
object SegmentSource {
    const val ATHLETE = "athlete"
    const val OSM = "osm"
    const val AGENCY = "agency"
}

/**
 * `app.activitytracker.feed.segment`: a named, public stretch of route for one sport group. Stored only in its creator's repo; the AppView
 * computes its length from [line] and indexes it only when that's 200 m to 50 km. Keyed by TID, except the segment importer's, keyed by the
 * [SegmentCandidate] id they were published from.
 */
@Serializable
data class SegmentRecord(
    /** At most 100 graphemes, not empty once trimmed. */
    val name: String,
    /** A [SegmentSportGroup] value. */
    val sport: String,
    /** The line from start to end as an encoded polyline at precision 6 (1e-6 degrees), at least two positions. */
    val line: String,
    /** A [SegmentSource] value. */
    val source: String,
    /** The data's licence, as an SPDX identifier such as `CC0-1.0`. */
    val license: String,
    val createdAt: Instant,
    /** The activity the line was traced from, if any. */
    val derivedFrom: StrongRef? = null,
    /** Who to credit for the line's data, such as "USDA Forest Service": at most 100 graphemes, shown only for [SegmentSource.AGENCY]. */
    val attribution: String? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_SEGMENT,
)

/**
 * `app.activitytracker.actor.segmentPrefs`: whether the athlete's segment matching is on, and whether they hide from leaderboards. It lives
 * only in their [AthleteSpace.SELF] space; no record means they were never asked, and matching is off.
 */
@Serializable
data class SegmentPrefsRecord(
    val matching: Boolean,
    val updatedAt: Instant,
    /** The athlete appears on no segment leaderboard, to anyone, themselves included; null means false. Never served. */
    val hideFromLeaderboards: Boolean? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_SEGMENT_PREFS,
) {
    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.actor.leaderboardBrackets`: the athlete chose age, weight and gender leaderboards (segments/leaderboard-brackets),
 * and these are their brackets, computed on the device from their own details. It lives only in their [AthleteSpace.SELF] space, only while
 * the choice is on, and never holds the details themselves. Each bracket is null when the athlete hasn't given its detail.
 */
@Serializable
data class LeaderboardBracketsRecord(
    val updatedAt: Instant,
    /** A [LeaderboardAgeGroup] value. */
    val ageGroup: String? = null,
    /** A [LeaderboardWeightClass] value. */
    val weightClass: String? = null,
    /** A [LeaderboardGender] value. */
    val gender: String? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_LEADERBOARD_BRACKETS,
) {
    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.feed.segmentStar`: the athlete starred the segment [subject]. It lives only in their [AthleteSpace.SELF] space and
 * only they read it: the AppView never stores it. Its rkey is derived from [subject]'s URI (`segmentStarRkey` in `core/atproto`), so
 * there's at most one per segment and unstarring needs no lookup.
 */
@Serializable
data class SegmentStarRecord(
    val subject: StrongRef,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_SEGMENT_STAR,
)

/**
 * `app.activitytracker.feed.segmentNameProposal`: a proposed new name for [segment] (segments/segment-names). Public, so it lives only in
 * the proposer's repo and names them. Rewriting it with a different name while it's open clears its votes.
 */
@Serializable
data class SegmentNameProposalRecord(
    val segment: AtUri,
    /** At most 100 graphemes, not empty once trimmed. */
    val name: String,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_SEGMENT_NAME_PROPOSAL,
) {
    companion object {
        const val MAX_NAME_GRAPHEMES = 100
    }
}

/** `app.activitytracker.feed.segmentNameVote#direction` values. */
object SegmentNameVoteDirection {
    const val UP = "up"
    const val DOWN = "down"
}

/**
 * `app.activitytracker.feed.segmentNameVote`: a vote on a [SegmentNameProposalRecord] at the version [subject] names. Private, so it lives
 * only in the voter's [AthleteSpace.INTERACTIONS] space; the AppView serves only totals.
 */
@Serializable
data class SegmentNameVoteRecord(
    val subject: StrongRef,
    /** A [SegmentNameVoteDirection] value; any other is ignored. */
    val direction: String,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_SEGMENT_NAME_VOTE,
)

/**
 * `app.activitytracker.feed.activitySource`: the file an activity was imported from, gzipped, under its activity's rkey. It can hold device
 * serial numbers and exact positions, so it's stored only in the athlete's [AthleteSpace.SELF] space, whatever the activity's visibility,
 * and only the athlete reads it: the AppView ignores it. Activities imported from one logbook file each have their own record naming the
 * same blob, and their [entry].
 */
@Serializable
data class ActivitySourceRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    /** A [SourceFormat] value. */
    val format: String,
    /** The file's name as picked. */
    val fileName: String,
    /** The file's size as picked, in bytes. */
    val size: Long,
    /** True when the app gzipped the file to store it; false when it came gzipped and [file] is the file as picked. */
    val compressed: Boolean,
    val createdAt: Instant,
    /** For a logbook file (UDDF, Subsurface): the index, in file order, of the dive this activity was read from. */
    val entry: Int? = null,
    /** Required by the lexicon: null only in the outbox, which uploads the blob and fills it in before `putRecord`. */
    val file: BlobRef? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_SOURCE,
) {
    companion object {
        /** The record's blob field, for [file]. */
        const val FILE_FIELD = "file"
        const val MIME_TYPE = "application/gzip"

        /** The lexicon's `maxSize` for [file]. */
        const val MAX_FILE_BYTES = 10_000_000L
    }
}

/** Known values of [ActivitySourceRecord.format], an open string. */
object SourceFormat {
    const val FIT = "fit"
    const val GPX = "gpx"
    const val TCX = "tcx"
    const val SHEARWATER = "shearwater"
    const val UDDF = "uddf"
    const val SUBSURFACE = "subsurface"
}

/**
 * `app.activitytracker.feed.activityDevices`: the recording device and the sensors an imported activity's file names, with the identifiers
 * that tell one device from another across activities, under its activity's rkey. It holds serial numbers, so it's stored only in the
 * athlete's [AthleteSpace.SELF] space, whatever the activity's visibility, and only the athlete reads it: the AppView ignores it.
 */
@Serializable
data class ActivityDevicesRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    /** The recording device first, then each paired sensor in the order the file first lists them. */
    val devices: List<ActivityDevice>,
    val createdAt: Instant,
    /** The activity profile the device recorded under, as the device names it (FIT session `sport_profile_name`), e.g. "MOUNTAIN". */
    val activityProfile: String? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_DEVICES,
) {
    companion object {
        /** The lexicon's `maxLength` for [devices]. */
        const val MAX_DEVICES = 32
    }
}

/** `app.activitytracker.feed.activityDevices#device`: a recording device or a paired sensor, as the file names it. */
@Serializable
data class ActivityDevice(
    /** A [DeviceRole] value. */
    val role: String,
    /** For a sensor, a [SensorType] value. */
    val kind: String? = null,
    /** "SRAM". */
    val manufacturer: String? = null,
    /** "HRM 200", or the file's product number when it has no name. */
    val model: String? = null,
    /** As the file writes it: decimal for FIT and TCX, as written for dive logs. */
    val serialNumber: String? = null,
    /** 1 to 65535. */
    val antDeviceNumber: Int? = null,
    /** 0 to 255. */
    val antTransmissionType: Int? = null,
    /** A [DeviceConnection] value. */
    val connection: String? = null,
    /** "31.33". */
    val softwareVersion: String? = null,
    /** A [DeviceBattery] value, as the activity ended. */
    val battery: String? = null,
    /** 0 to 100. */
    val batteryPercent: Int? = null,
    /** Millivolts, as the activity ended. */
    val batteryVoltage: Int? = null,
    /** Seconds the device has operated over its life, as the activity ended (FIT `cum_operating_time`). */
    val operatingTime: Long? = null,
) {
    companion object {
        /** The lexicon's `maxLength` for [manufacturer], [model] and [ActivityDevicesRecord.activityProfile]. */
        const val MAX_NAME = 128

        /** The lexicon's `maxLength` for [serialNumber] and [softwareVersion]. */
        const val MAX_IDENTIFIER = 64
        const val MAX_PERCENT = 100

        /** The lexicon's bounds for [antDeviceNumber] and [antTransmissionType]. */
        val ANT_DEVICE_NUMBERS = 1..65_535
        val ANT_TRANSMISSION_TYPES = 0..255
    }
}

/** Known values of [ActivityDevice.role], an open string. */
object DeviceRole {
    const val RECORDER = "recorder"
    const val SENSOR = "sensor"
}

/** Known values of [ActivityDevice.connection], an open string. */
object DeviceConnection {
    const val ANT_PLUS = "antPlus"
    const val ANT = "ant"
    const val BLUETOOTH_LOW_ENERGY = "bluetoothLowEnergy"
    const val BLUETOOTH = "bluetooth"
    const val WIFI = "wifi"
    const val LOCAL = "local"
}

/** Known values of [ActivityDevice.battery], an open string. */
object DeviceBattery {
    const val NEW = "new"
    const val GOOD = "good"
    const val OK = "ok"
    const val LOW = "low"
    const val CRITICAL = "critical"
    const val CHARGING = "charging"
}

/**
 * `app.activitytracker.feed.activityZones`: the athlete's zones for an activity, under its activity's rkey, so later threshold changes
 * don't change its time in zone, and the activity's [trainingLoad].
 *
 * Zone bounds and training load reveal thresholds, so it's stored at the narrowest of the activity's heart rate and power visibilities and
 * the athlete's training visibility: in the repo when all are public, otherwise in their tier space ([AthleteSpace]). Where it's stored is
 * its only visibility.
 */
@Serializable
data class ActivityZonesRecord(
    /** The activity's URI; it shares this record's rkey. */
    val activity: AtUri,
    val createdAt: Instant,
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
    val trainingLoad: TrainingLoad? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_ACTIVITY_ZONES,
)

/** `app.activitytracker.feed.activityZones#trainingLoad`: the activity's training load as the recording device computed it, and its HRV. */
@Immutable
@Serializable
data class TrainingLoad(
    /** Functional threshold power, in watts. */
    val ftp: Int? = null,
    /** Normalized power over [ftp], in hundredths. */
    val intensityFactor: Int? = null,
    /** Tenths. */
    val trainingStressScore: Int? = null,
    /** Tenths, from 0 to 50. */
    val aerobicTrainingEffect: Int? = null,
    /** Tenths, from 0 to 50. */
    val anaerobicTrainingEffect: Int? = null,
    /** Garmin's exercise load: the EPOC-based training load, in whole units, from 0 to 10,000. */
    val exerciseLoad: Int? = null,
    /** Stamina potential at the start, in percent. */
    val staminaStart: Int? = null,
    /** Stamina potential at the end, in percent. */
    val staminaEnd: Int? = null,
    /** The lowest stamina during the activity, in percent. */
    val staminaMin: Int? = null,
    /** Estimated sweat loss, in milliliters, from 0 to 20,000. */
    val sweatLoss: Int? = null,
    /** Heart rate variability: the RMSSD of the activity's beat-to-beat intervals, in milliseconds, computed at import. */
    val hrvRmssd: Int? = null,
) {
    /** Whether this holds nothing, so there's no load to store. */
    val isEmpty: Boolean
        get() = this == TrainingLoad()

    companion object {
        const val MAX_EXERCISE_LOAD = 10_000
        const val MAX_STAMINA = 100
        const val MAX_SWEAT_LOSS = 20_000
    }
}

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
    /** Tenths of a breath per minute. */
    val respirationRate: List<Int?>? = null,
    /** Millimeters of vertical bounce per step. */
    val verticalOscillation: List<Int?>? = null,
    /** Milliseconds of ground contact per step. */
    val stanceTime: List<Int?>? = null,
    /** Millimeters. */
    val stepLength: List<Int?>? = null,
    /** [verticalOscillation] over [stepLength], in hundredths of a percent. */
    val verticalRatio: List<Int?>? = null,
    /** The left foot's share of ground contact time, in hundredths of a percent. */
    val stanceTimeBalance: List<Int?>? = null,
    /** Percent of power from the right leg. */
    val leftRightBalance: List<Int?>? = null,
    /** Partial pressure of the oxygen breathed, in millibar. */
    val ppO2: List<Int?>? = null,
    /** Central nervous system oxygen toxicity, in percent of the limit. */
    val cns: List<Int?>? = null,
    /** Nitrogen loading, in percent of the limit. */
    val n2Load: List<Int?>? = null,
    /** No-decompression limit in seconds, as the dive computer recorded it (0 while in deco). */
    val ndl: List<Int?>? = null,
    /** The next decompression stop's depth (the ceiling), in centimeters; null when not in deco. */
    val decoStopDepth: List<Int?>? = null,
    /** The next decompression stop's duration, in seconds. */
    val decoStopTime: List<Int?>? = null,
    /** Time to surface, in seconds. */
    val tts: List<Int?>? = null,
    /** Each switch of breathing gas, in time order; the first gas breathed is a switch at time 0. */
    val gasSwitches: List<GasSwitch>? = null,
    /** Each alert the dive computer raised, in time order. */
    val diveAlerts: List<DiveAlert>? = null,
    /** One pressure series per tank, in the order of the activity's `dive.tanks`. */
    val tankPressures: List<TankPressure>? = null,
    /** The recording's laps, in order; null when its only lap spans the activity. */
    val laps: List<Lap>? = null,
    /** A pool swim's lengths, in order, rests included. */
    val lengths: List<Length>? = null,
    /** A workout's sets, in order, rests included. */
    val sets: List<WorkoutSet>? = null,
    /** A multisport activity's legs, in order, transitions included; null when the recording has one sport. */
    val legs: List<Leg>? = null,
    /** The left leg's torque effectiveness, in hundredths of a percent. */
    val torqueEffectivenessLeft: List<Int?>? = null,
    /** The right leg's torque effectiveness, in hundredths of a percent. */
    val torqueEffectivenessRight: List<Int?>? = null,
    /** The left leg's pedal smoothness, in hundredths of a percent. */
    val pedalSmoothnessLeft: List<Int?>? = null,
    /** The right leg's pedal smoothness, in hundredths of a percent. */
    val pedalSmoothnessRight: List<Int?>? = null,
    /** The left pedal's platform centre offset, in millimeters; negative is inboard. */
    val pcoLeft: List<Int?>? = null,
    /** The right pedal's platform centre offset, in millimeters; negative is inboard. */
    val pcoRight: List<Int?>? = null,
    /** Core body temperature, in hundredths of a degree Celsius. */
    val coreTemperature: List<Int?>? = null,
    /** Muscle oxygen saturation, in tenths of a percent. */
    val smO2: List<Int?>? = null,
    /** Total hemoglobin concentration, in hundredths of a gram per deciliter. */
    val totalHemoglobin: List<Int?>? = null,
    /** An e-bike's battery level, in percent. */
    val ebikeBattery: List<Int?>? = null,
    /** An e-bike's assist mode, as the bike numbers it (0 is off). */
    val ebikeAssistMode: List<Int?>? = null,
    /** An e-bike's assist level, in percent. */
    val ebikeAssistLevel: List<Int?>? = null,
    /** Running form power from a Stryd, in watts. */
    val formPower: List<Int?>? = null,
    /** Leg spring stiffness from a Stryd, in hundredths of a kilonewton per meter. */
    val legSpringStiffness: List<Int?>? = null,
) {
    /** The [StreamChannel]s this holds. */
    val channels: List<String>
        get() =
            INT_CHANNELS.filter { it.get(this) != null }.map { it.token } +
                listOfNotNull(
                    StreamChannel.GEARS.takeIf { gears != null },
                    StreamChannel.GAS_SWITCHES.takeIf { gasSwitches != null },
                    StreamChannel.DIVE_ALERTS.takeIf { diveAlerts != null },
                    StreamChannel.TANK_PRESSURES.takeIf { tankPressures != null },
                    StreamChannel.LAPS.takeIf { laps != null },
                    StreamChannel.LENGTHS.takeIf { lengths != null },
                    StreamChannel.SETS.takeIf { sets != null },
                    StreamChannel.LEGS.takeIf { legs != null },
                )

    /**
     * A per-sample integer channel, lined up with [time]: its [token], how to read it from the data, and how to replace it. Code that
     * treats channels alike (merging, export) iterates [INT_CHANNELS] instead of listing fields.
     */
    class IntChannel(
        val token: String,
        val get: (ActivityStreamData) -> List<Int?>?,
        val with: (ActivityStreamData, List<Int?>?) -> ActivityStreamData,
    )

    companion object {
        /** Every per-sample integer channel, in the order [channels] lists them. */
        val INT_CHANNELS: List<IntChannel> =
            listOf(
                IntChannel(StreamChannel.DISTANCE, { it.distance }) { d, v -> d.copy(distance = v) },
                IntChannel(StreamChannel.ALTITUDE, { it.altitude }) { d, v -> d.copy(altitude = v) },
                IntChannel(StreamChannel.SPEED, { it.speed }) { d, v -> d.copy(speed = v) },
                IntChannel(StreamChannel.HEART_RATE, { it.heartRate }) { d, v -> d.copy(heartRate = v) },
                IntChannel(StreamChannel.POWER, { it.power }) { d, v -> d.copy(power = v) },
                IntChannel(StreamChannel.CADENCE, { it.cadence }) { d, v -> d.copy(cadence = v) },
                IntChannel(StreamChannel.TEMPERATURE, { it.temperature }) { d, v -> d.copy(temperature = v) },
                IntChannel(StreamChannel.DEPTH, { it.depth }) { d, v -> d.copy(depth = v) },
                IntChannel(StreamChannel.RESPIRATION_RATE, { it.respirationRate }) { d, v -> d.copy(respirationRate = v) },
                IntChannel(StreamChannel.VERTICAL_OSCILLATION, { it.verticalOscillation }) { d, v -> d.copy(verticalOscillation = v) },
                IntChannel(StreamChannel.STANCE_TIME, { it.stanceTime }) { d, v -> d.copy(stanceTime = v) },
                IntChannel(StreamChannel.STEP_LENGTH, { it.stepLength }) { d, v -> d.copy(stepLength = v) },
                IntChannel(StreamChannel.VERTICAL_RATIO, { it.verticalRatio }) { d, v -> d.copy(verticalRatio = v) },
                IntChannel(StreamChannel.STANCE_TIME_BALANCE, { it.stanceTimeBalance }) { d, v -> d.copy(stanceTimeBalance = v) },
                IntChannel(StreamChannel.LEFT_RIGHT_BALANCE, { it.leftRightBalance }) { d, v -> d.copy(leftRightBalance = v) },
                IntChannel(StreamChannel.PP_O2, { it.ppO2 }) { d, v -> d.copy(ppO2 = v) },
                IntChannel(StreamChannel.CNS, { it.cns }) { d, v -> d.copy(cns = v) },
                IntChannel(StreamChannel.N2_LOAD, { it.n2Load }) { d, v -> d.copy(n2Load = v) },
                IntChannel(StreamChannel.NDL, { it.ndl }) { d, v -> d.copy(ndl = v) },
                IntChannel(StreamChannel.DECO_STOP_DEPTH, { it.decoStopDepth }) { d, v -> d.copy(decoStopDepth = v) },
                IntChannel(StreamChannel.DECO_STOP_TIME, { it.decoStopTime }) { d, v -> d.copy(decoStopTime = v) },
                IntChannel(StreamChannel.TTS, { it.tts }) { d, v -> d.copy(tts = v) },
                IntChannel(StreamChannel.TORQUE_EFFECTIVENESS_LEFT, { it.torqueEffectivenessLeft }) { d, v ->
                    d.copy(torqueEffectivenessLeft = v)
                },
                IntChannel(StreamChannel.TORQUE_EFFECTIVENESS_RIGHT, { it.torqueEffectivenessRight }) { d, v ->
                    d.copy(torqueEffectivenessRight = v)
                },
                IntChannel(StreamChannel.PEDAL_SMOOTHNESS_LEFT, { it.pedalSmoothnessLeft }) { d, v -> d.copy(pedalSmoothnessLeft = v) },
                IntChannel(StreamChannel.PEDAL_SMOOTHNESS_RIGHT, { it.pedalSmoothnessRight }) { d, v -> d.copy(pedalSmoothnessRight = v) },
                IntChannel(StreamChannel.PCO_LEFT, { it.pcoLeft }) { d, v -> d.copy(pcoLeft = v) },
                IntChannel(StreamChannel.PCO_RIGHT, { it.pcoRight }) { d, v -> d.copy(pcoRight = v) },
                IntChannel(StreamChannel.CORE_TEMPERATURE, { it.coreTemperature }) { d, v -> d.copy(coreTemperature = v) },
                IntChannel(StreamChannel.SM_O2, { it.smO2 }) { d, v -> d.copy(smO2 = v) },
                IntChannel(StreamChannel.TOTAL_HEMOGLOBIN, { it.totalHemoglobin }) { d, v -> d.copy(totalHemoglobin = v) },
                IntChannel(StreamChannel.EBIKE_BATTERY, { it.ebikeBattery }) { d, v -> d.copy(ebikeBattery = v) },
                IntChannel(StreamChannel.EBIKE_ASSIST_MODE, { it.ebikeAssistMode }) { d, v -> d.copy(ebikeAssistMode = v) },
                IntChannel(StreamChannel.EBIKE_ASSIST_LEVEL, { it.ebikeAssistLevel }) { d, v -> d.copy(ebikeAssistLevel = v) },
                IntChannel(StreamChannel.FORM_POWER, { it.formPower }) { d, v -> d.copy(formPower = v) },
                IntChannel(StreamChannel.LEG_SPRING_STIFFNESS, { it.legSpringStiffness }) { d, v -> d.copy(legSpringStiffness = v) },
            )
    }
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

/** `app.activitytracker.feed.activityStreams#gasSwitch`: a switch to a breathing gas, in whole percents (air is 21/0). */
@Immutable
@Serializable
data class GasSwitch(
    /** Seconds since the activity's `startedAt`. */
    val time: Int,
    val oxygen: Int,
    val helium: Int,
)

/** `app.activitytracker.feed.activityStreams#diveAlert`: an alert the dive computer raised. */
@Immutable
@Serializable
data class DiveAlert(
    /** Seconds since the activity's `startedAt`. */
    val time: Int,
    /** A [DiveAlertKind] value, or the file's own name for one it doesn't list. At most [MAX_KIND_LENGTH]. */
    val kind: String,
) {
    companion object {
        /** The lexicon's `maxLength` for [kind]. */
        const val MAX_KIND_LENGTH = 64
    }
}

/** Known values of [DiveAlert.kind], an open string: FIT's `dive_alert` names in lower camel case, then UDDF alarms FIT doesn't name. */
object DiveAlertKind {
    const val NDL_REACHED = "ndlReached"
    const val GAS_SWITCH_PROMPTED = "gasSwitchPrompted"
    const val NEAR_SURFACE = "nearSurface"
    const val APPROACHING_NDL = "approachingNdl"
    const val PO2_WARN = "po2Warn"
    const val PO2_CRIT_HIGH = "po2CritHigh"
    const val PO2_CRIT_LOW = "po2CritLow"
    const val TIME_ALERT = "timeAlert"
    const val DEPTH_ALERT = "depthAlert"
    const val DECO_CEILING_BROKEN = "decoCeilingBroken"
    const val DECO_COMPLETE = "decoComplete"
    const val SAFETY_STOP_BROKEN = "safetyStopBroken"
    const val SAFETY_STOP_COMPLETE = "safetyStopComplete"
    const val CNS_WARNING = "cnsWarning"
    const val CNS_CRITICAL = "cnsCritical"
    const val OTU_WARNING = "otuWarning"
    const val OTU_CRITICAL = "otuCritical"
    const val ASCENT_CRITICAL = "ascentCritical"
    const val BATTERY_LOW = "batteryLow"
    const val BATTERY_CRITICAL = "batteryCritical"
    const val SAFETY_STOP_STARTED = "safetyStopStarted"
    const val APPROACHING_FIRST_DECO_STOP = "approachingFirstDecoStop"
    const val SETPOINT_SWITCH_AUTO_LOW = "setpointSwitchAutoLow"
    const val SETPOINT_SWITCH_AUTO_HIGH = "setpointSwitchAutoHigh"
    const val SETPOINT_SWITCH_MANUAL_LOW = "setpointSwitchManualLow"
    const val SETPOINT_SWITCH_MANUAL_HIGH = "setpointSwitchManualHigh"
    const val AUTO_SETPOINT_SWITCH_IGNORED = "autoSetpointSwitchIgnored"
    const val SWITCHED_TO_OPEN_CIRCUIT = "switchedToOpenCircuit"
    const val SWITCHED_TO_CLOSED_CIRCUIT = "switchedToClosedCircuit"
    const val TANK_BATTERY_LOW = "tankBatteryLow"
    const val PO2_CCR_DIL_LOW = "po2CcrDilLow"
    const val DECO_STOP_CLEARED = "decoStopCleared"
    const val APNEA_NEUTRAL_BUOYANCY = "apneaNeutralBuoyancy"
    const val APNEA_TARGET_DEPTH = "apneaTargetDepth"
    const val APNEA_SURFACE = "apneaSurface"
    const val APNEA_HIGH_SPEED = "apneaHighSpeed"
    const val APNEA_LOW_SPEED = "apneaLowSpeed"
    const val BREATH = "breath"
    const val ERROR = "error"
    const val LINK = "link"
    const val MICROBUBBLES = "microbubbles"
    const val RBT = "rbt"
    const val SKIN_COOLING = "skinCooling"
}

/** `app.activitytracker.feed.activityStreams#tankPressure`: one tank's pressure over the dive. */
@Immutable
@Serializable
data class TankPressure(
    /** Millibar, lined up with [ActivityStreamData.time]; null where the tank has no reading. */
    val pressure: List<Int?>
)

/** `app.activitytracker.feed.activityStreams#lap`: a lap as the recording device split it. */
@Immutable
@Serializable
data class Lap(
    /** Seconds since the activity's `startedAt`. */
    val start: Int,
    /** Seconds. */
    val elapsed: Int,
    /** Seconds spent moving. */
    val moving: Int? = null,
    /** Meters. */
    val distance: Int? = null,
    /** Millimeters per second. */
    val avgSpeed: Int? = null,
    /** Millimeters per second. */
    val maxSpeed: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Watts. */
    val avgPower: Int? = null,
    /** Revolutions or steps per minute. */
    val avgCadence: Int? = null,
    /** Meters. */
    val ascent: Int? = null,
    /** Meters. */
    val descent: Int? = null,
    /** Kilocalories. */
    val calories: Int? = null,
    /** A [LapIntensity] value. */
    val intensity: String? = null,
    /** A [LapTrigger] value: what ended the lap. */
    val trigger: String? = null,
)

/** Known values of [Lap.intensity], an open string. */
object LapIntensity {
    const val ACTIVE = "active"
    const val REST = "rest"
    const val WARMUP = "warmup"
    const val COOLDOWN = "cooldown"
    const val RECOVERY = "recovery"
    const val INTERVAL = "interval"
}

/** Known values of [Lap.trigger], an open string. */
object LapTrigger {
    const val MANUAL = "manual"
    const val TIME = "time"
    const val DISTANCE = "distance"
    const val POSITION = "position"
    const val HEART_RATE = "heartRate"
    const val SESSION_END = "sessionEnd"
    const val FITNESS_EQUIPMENT = "fitnessEquipment"
}

/** `app.activitytracker.feed.activityStreams#length`: one length of a pool swim. */
@Immutable
@Serializable
data class Length(
    /** Seconds since the activity's `startedAt`. */
    val start: Int,
    /** Seconds. */
    val elapsed: Int,
    /** A [SwimStroke] value. */
    val stroke: String? = null,
    /** Stroke count. */
    val strokes: Int? = null,
    /** True for an idle length (a rest at the wall). */
    val rest: Boolean? = null,
)

/** Known values of [Length.stroke], an open string. */
object SwimStroke {
    const val FREESTYLE = "freestyle"
    const val BACKSTROKE = "backstroke"
    const val BREASTSTROKE = "breaststroke"
    const val BUTTERFLY = "butterfly"
    const val DRILL = "drill"
    const val MIXED = "mixed"
    const val IM = "im"
}

/**
 * `app.activitytracker.feed.activityStreams#set`: one set of a strength, HIIT or cardio workout, or a rest between sets. [category] and
 * [exercise] are the FIT profile's tokens in snake case (`bench_press`, `single_arm_cable_row`), open strings.
 */
@Immutable
@Serializable
data class WorkoutSet(
    /** Seconds since the activity's `startedAt`. */
    val start: Int,
    /** Seconds. */
    val elapsed: Int,
    /** True for a rest between sets. */
    val rest: Boolean? = null,
    val category: String? = null,
    /** The exercise within [category]. */
    val exercise: String? = null,
    val reps: Int? = null,
    /** Grams. */
    val weight: Int? = null,
)

/** `app.activitytracker.feed.activityStreams#leg`: one leg of a multisport activity, or a transition between legs. */
@Immutable
@Serializable
data class Leg(
    /** Seconds since the activity's `startedAt`. */
    val start: Int,
    /** Seconds. */
    val elapsed: Int,
    /** A [Sport] value; [Sport.MULTISPORT] for a transition. */
    val sport: String,
    /** A [SubSport] value refining [sport]. */
    val subSport: String? = null,
    /** True for a transition between legs. */
    val transition: Boolean? = null,
    /** Seconds spent moving. */
    val moving: Int? = null,
    /** Meters. */
    val distance: Int? = null,
    /** Millimeters per second. */
    val avgSpeed: Int? = null,
    /** Millimeters per second. */
    val maxSpeed: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Watts. */
    val avgPower: Int? = null,
    /** Revolutions or steps per minute. */
    val avgCadence: Int? = null,
    /** Meters. */
    val ascent: Int? = null,
    /** Meters. */
    val descent: Int? = null,
    /** Kilocalories. */
    val calories: Int? = null,
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
    const val RESPIRATION_RATE = "respirationRate"
    const val VERTICAL_OSCILLATION = "verticalOscillation"
    const val STANCE_TIME = "stanceTime"
    const val STEP_LENGTH = "stepLength"
    const val VERTICAL_RATIO = "verticalRatio"
    const val STANCE_TIME_BALANCE = "stanceTimeBalance"
    const val LEFT_RIGHT_BALANCE = "leftRightBalance"
    const val PP_O2 = "ppO2"
    const val CNS = "cns"
    const val N2_LOAD = "n2Load"
    const val NDL = "ndl"
    const val DECO_STOP_DEPTH = "decoStopDepth"
    const val DECO_STOP_TIME = "decoStopTime"
    const val TTS = "tts"
    const val TORQUE_EFFECTIVENESS_LEFT = "torqueEffectivenessLeft"
    const val TORQUE_EFFECTIVENESS_RIGHT = "torqueEffectivenessRight"
    const val PEDAL_SMOOTHNESS_LEFT = "pedalSmoothnessLeft"
    const val PEDAL_SMOOTHNESS_RIGHT = "pedalSmoothnessRight"
    const val PCO_LEFT = "pcoLeft"
    const val PCO_RIGHT = "pcoRight"
    const val CORE_TEMPERATURE = "coreTemperature"
    const val SM_O2 = "smO2"
    const val TOTAL_HEMOGLOBIN = "totalHemoglobin"
    const val EBIKE_BATTERY = "ebikeBattery"
    const val EBIKE_ASSIST_MODE = "ebikeAssistMode"
    const val EBIKE_ASSIST_LEVEL = "ebikeAssistLevel"
    const val FORM_POWER = "formPower"
    const val LEG_SPRING_STIFFNESS = "legSpringStiffness"
    const val GAS_SWITCHES = "gasSwitches"
    const val DIVE_ALERTS = "diveAlerts"
    const val TANK_PRESSURES = "tankPressures"
    const val LAPS = "laps"
    const val LENGTHS = "lengths"
    const val SETS = "sets"
    const val LEGS = "legs"
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
    /** Nitrogen loading at the start, in percent of the limit. */
    val startN2: Int? = null,
    /** Nitrogen loading at the end, in percent of the limit. */
    val endN2: Int? = null,
    /** Millimeters per second. */
    val avgAscentRate: Long? = null,
    /** Millimeters per second. */
    val maxAscentRate: Long? = null,
    /** Millimeters per second. */
    val avgDescentRate: Long? = null,
    /** Millimeters per second. */
    val maxDescentRate: Long? = null,
    /** Seconds from leaving the surface to reaching the maximum depth. */
    val descentTime: Long? = null,
    /** A [WaterType] value: the water the dive computer was set for. */
    val waterType: String? = null,
    /** Kilograms per cubic meter. */
    val waterDensity: Int? = null,
    /** A [DecoModel] value: the decompression model the dive computer was set to. */
    val decoModel: String? = null,
    /** The ppO₂ the dive computer warns at, in millibar. */
    val ppO2Warn: Int? = null,
    /** The ppO₂ the dive computer treats as critical, in millibar. */
    val ppO2Critical: Int? = null,
    /** The ppO₂ limit for decompression gases, in millibar. */
    val ppO2Deco: Int? = null,
    /** A closed-circuit rebreather's low setpoint, in millibar. */
    val lowSetpoint: Int? = null,
    /** A closed-circuit rebreather's high setpoint, in millibar. */
    val highSetpoint: Int? = null,
    /** The safety stop the dive computer was set for, in seconds; null when the safety stop was off. */
    val safetyStopTime: Int? = null,
    /** Tenths of a degree Celsius; may be negative. */
    val airTemperature: Long? = null,
    /** Centimeters. */
    val visibility: Long? = null,
    /** The site's altitude above sea level, in meters. */
    val altitude: Long? = null,
    /** Seconds after the dive before flying. */
    val noFlyTime: Long? = null,
    /** Seconds after the dive until fully desaturated. */
    val desaturationTime: Long? = null,
    /** The highest partial pressure of oxygen breathed, in millibar. */
    val highestPpO2: Int? = null,
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
    /** The tank's water volume, in milliliters. */
    val volume: Long? = null,
    /** The gas the tank held. */
    val gas: ActivityDiveGas? = null,
)

/** Known values of [ActivityDive.waterType], an open string. */
object WaterType {
    const val FRESH = "fresh"
    const val SALT = "salt"
    const val EN13319 = "en13319"
    const val CUSTOM = "custom"
}

/** Known values of [ActivityDive.decoModel], an open string. */
object DecoModel {
    /** Bühlmann ZHL-16C with [ActivityDive.gradientFactorLow] and [ActivityDive.gradientFactorHigh]. */
    const val ZHL16C = "zhl16c"
    const val VPM_B = "vpmB"
    const val VPM_B_GFS = "vpmBGfs"
    const val DCIEM = "dciem"
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

/**
 * `app.activitytracker.graph.block`: the athlete blocks [subject]. It lives only in their [AthleteSpace.INTERACTIONS] space, so only they
 * and the AppView learn of it, and its rkey is [subject]'s DID ([rkey]).
 */
@Serializable
data class BlockRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.GRAPH_BLOCK,
) {
    companion object {
        fun rkey(subject: Did): String = subject.value
    }
}

/**
 * `app.activitytracker.graph.mute`: the athlete mutes [subject]. It lives only in their [AthleteSpace.INTERACTIONS] space, so only they and
 * the AppView learn of it, and its rkey is [subject]'s DID ([rkey]).
 */
@Serializable
data class MuteRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.GRAPH_MUTE,
) {
    companion object {
        fun rkey(subject: Did): String = subject.value
    }
}

/** `app.bsky.graph.block`, as the AppView reads it from an athlete's repo. Clients never write it. */
@Serializable
data class BskyBlockRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.BSKY_GRAPH_BLOCK,
)

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
 * `app.activitytracker.actor.activityDefaults`: the [Visibility] values an athlete's new activities and their [DetailCategory]s start with.
 * It lives only in their [AthleteSpace.SELF] space. A missing record or [visibility] means [Visibility.PUBLIC], and a category with no
 * default starts at [DetailCategory.defaultWhenUnset] (the activity's, but Only me for gear); read it through [resolved], which also caps
 * each category at the activity. A `chartsVisibility` left by older clients is ignored.
 */
@Serializable
data class ActivityDefaultsRecord(
    val visibility: String? = null,
    val details: DetailVisibilities? = null,
    @SerialName($$"$type") val type: String = Nsid.ACTOR_ACTIVITY_DEFAULTS,
) {
    /** (activity, each [DetailCategory.all] to its visibility), each a known [Visibility] value, no category wider than the activity. */
    fun resolved(): Pair<String, Map<String, String>> {
        val activity = Visibility.known(visibility)
        return activity to
            DetailCategory.all.associateWith { category ->
                Visibility.capped(
                    details?.get(category)?.let { Visibility.known(it) } ?: DetailCategory.defaultWhenUnset(category, activity),
                    activity,
                )
            }
    }

    companion object {
        const val RKEY = "self"
    }
}

/**
 * `app.activitytracker.actor.activityDefaults#details`: one optional [Visibility] value per [DetailCategory], named by its token. Read and
 * build it through [get] and [of].
 */
@Serializable
data class DetailVisibilities(
    val startTime: String? = null,
    val heartRate: String? = null,
    val power: String? = null,
    val speed: String? = null,
    val calories: String? = null,
    val map: String? = null,
    val physiology: String? = null,
    val cadence: String? = null,
    val dive: String? = null,
    val gear: String? = null,
) {
    /** [category]'s value, or null when it's unset or not a known category. */
    operator fun get(category: String): String? =
        when (category) {
            DetailCategory.START_TIME -> startTime
            DetailCategory.HEART_RATE -> heartRate
            DetailCategory.POWER -> power
            DetailCategory.SPEED -> speed
            DetailCategory.CALORIES -> calories
            DetailCategory.MAP -> map
            DetailCategory.PHYSIOLOGY -> physiology
            DetailCategory.CADENCE -> cadence
            DetailCategory.DIVE -> dive
            DetailCategory.GEAR -> gear
            else -> null
        }

    companion object {
        /** [values] by category token; unknown tokens are dropped. */
        fun of(values: Map<String, String>): DetailVisibilities =
            DetailVisibilities(
                startTime = values[DetailCategory.START_TIME],
                heartRate = values[DetailCategory.HEART_RATE],
                power = values[DetailCategory.POWER],
                speed = values[DetailCategory.SPEED],
                calories = values[DetailCategory.CALORIES],
                map = values[DetailCategory.MAP],
                physiology = values[DetailCategory.PHYSIOLOGY],
                cadence = values[DetailCategory.CADENCE],
                dive = values[DetailCategory.DIVE],
                gear = values[DetailCategory.GEAR],
            )
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
 * sent, and the polyline detail they were built at. It lives only in their [AthleteSpace.SELF] space, apart from the zones so finishing a
 * re-trim never overwrites a zone saved since.
 */
@Serializable
data class RouteTrimRecord(
    /** Null when the athlete has never saved zones. */
    val revision: String? = null,
    val updatedAt: Instant,
    /** The polyline detail the polylines were rebuilt at; null means 0, the first. */
    val lineVersion: Int? = null,
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

    /** [value], narrowed to [cap] if it's wider: a detail category is never more visible than its activity. */
    fun capped(value: String, cap: String): String = narrower(value, cap)

    /** The values no wider than [cap], widest first: what a category picker offers for an activity at [cap]. */
    fun atMost(cap: String): List<String> = all.drop(rank(cap))

    /** [value] if it's a known value, [PUBLIC] when it's absent, else [SELF]. */
    fun known(value: String?): String =
        when (value) {
            null -> PUBLIC
            in all -> value
            else -> SELF
        }
}
