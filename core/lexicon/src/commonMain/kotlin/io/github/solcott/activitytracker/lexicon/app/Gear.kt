package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `app.activitytracker.gear.item`: one piece of the athlete's gear, keyed by a TID. A Public item is in the athlete's repo, any other in
 * their tier space for its visibility ([AthleteSpace]); it keeps its rkey when it moves, so its canonical URI is
 * `at://{did}/app.activitytracker.gear.item/{rkey}` wherever it's stored.
 */
@Immutable
@Serializable
data class GearItemRecord(
    val name: String,
    /** A [GearType] value; one this client doesn't know is shown as [GearType.OTHER] and kept. */
    val type: String,
    val createdAt: Instant,
    /** One of [GearType.kinds] for [type]; one this client doesn't know is shown as the type alone and kept. */
    val kind: String? = null,
    val brand: String? = null,
    val model: String? = null,
    /** [Sport] values. */
    val sports: List<String>? = null,
    /** Grams. */
    val weight: Int? = null,
    val notes: String? = null,
    /** Its blob is stored where the record is. */
    val photo: ActivityMedia.Image? = null,
    val acquiredAt: Instant? = null,
    /** When the athlete retired it; null while it's in use. */
    val retiredAt: Instant? = null,
    /** Meters it was used for before the athlete tracked it; added to its usage totals, never to their breakdowns. */
    val startingDistance: Long? = null,
    /** Seconds it was used for before the athlete tracked it; added to its usage totals, never to their breakdowns. */
    val startingTime: Long? = null,
    /** The items it was mounted on, oldest first, at most [MAX_MOUNTS]; [GearMounts] checks them. */
    val mounts: List<GearMount>? = null,
    @SerialName($$"$type") val recordType: String = Nsid.GEAR_ITEM,
) {
    companion object {
        /** The lexicon's `maxGraphemes` for [name]. */
        const val MAX_NAME = 100

        /** The lexicon's `maxGraphemes` for [brand] and [model]. */
        const val MAX_BRAND = 128

        /** The lexicon's `maxGraphemes` for [notes]. */
        const val MAX_NOTES = 1000

        /** The lexicon's `maxLength` for [sports]. */
        const val MAX_SPORTS = 16

        /** The lexicon's `maximum` for [weight], in grams. */
        const val MAX_WEIGHT = 1_000_000

        /** The lexicon's `maxLength` for [mounts]. */
        const val MAX_MOUNTS = 50
    }
}

/**
 * `app.activitytracker.gear.item#mount`: one period an item was mounted on [parent], another item of the same athlete, from [from]
 * (inclusive) until [to] (exclusive; null while still mounted).
 */
@Immutable
@Serializable
data class GearMount(
    /** The parent's canonical [GearItemRecord] URI. */
    val parent: AtUri,
    val from: Instant,
    val to: Instant? = null,
)

/**
 * `app.activitytracker.gear.autoAssign`: the gear each activity type starts with. It lives only in the athlete's [AthleteSpace.SELF] space,
 * and the AppView never indexes it. Resolve it for a type with `GearDefaults.forType` (core/data).
 */
@Serializable
data class GearAutoAssignRecord(
    val updatedAt: Instant,
    /** At most one entry per sport and sub-sport. */
    val sports: List<GearSportDefault>? = null,
    @SerialName($$"$type") val type: String = Nsid.GEAR_AUTO_ASSIGN,
) {
    companion object {
        const val RKEY = "self"

        /** The lexicon's `maxLength` for [sports]. */
        const val MAX_SPORTS = 256
    }
}

/**
 * `app.activitytracker.gear.collection`: a named group of the athlete's gear items, keyed by a TID. It lives only in the athlete's
 * [AthleteSpace.SELF] space, and the AppView never indexes it. An item can be in any number of collections; an activity a collection was
 * added to keeps the items, never the collection.
 */
@Immutable
@Serializable
data class GearCollectionRecord(
    val name: String,
    val createdAt: Instant,
    val notes: String? = null,
    /** Canonical [GearItemRecord] URIs in the athlete's order, at most [MAX_ITEMS]; readers skip ones that no longer exist. */
    val items: List<AtUri>? = null,
    @SerialName($$"$type") val type: String = Nsid.GEAR_COLLECTION,
) {
    companion object {
        /** The lexicon's `maxGraphemes` for [name]. */
        const val MAX_NAME = 100

        /** The lexicon's `maxGraphemes` for [notes]. */
        const val MAX_NOTES = 1000

        /** The lexicon's `maxLength` for [items]. */
        const val MAX_ITEMS = 50
    }
}

/** `app.activitytracker.gear.autoAssign#sportDefault`: the items an activity of [sport] (and [subSport], when set) starts with. */
@Serializable
data class GearSportDefault(
    /** A [Sport] value. */
    val sport: String,
    /** Canonical [GearItemRecord] URIs, at most [ActivityRecord.MAX_GEAR]. */
    val gear: List<AtUri>,
    /** A [SubSport] value; null for the sport in any form. */
    val subSport: String? = null,
)

/** `app.activitytracker.gear.defs#gearView`: a gear item as an activity shows it. */
@Immutable
@Serializable
data class GearView(
    /** The item's canonical URI. */
    val uri: AtUri,
    val name: String,
    /** A [GearType] value. */
    val type: String,
    val kind: String? = null,
    val brand: String? = null,
    val model: String? = null,
    /** A URL to load the item's photo from; null when it has none. */
    val photo: String? = null,
)

/**
 * `app.activitytracker.gear.defs#gearTotals`: usage totals over the activities counted for an item, for one viewer. Each activity counts
 * once, and only the activities whose gear the viewer may see.
 */
@Immutable
@Serializable
data class GearTotals(
    val activities: Long,
    /** Seconds: each activity's moving time, or its elapsed time when it has none. */
    val movingTime: Long,
    /** Meters. */
    val distance: Long,
    /** Meters. */
    val elevationGain: Long,
    /** Meters: vertical, for skis and boards. */
    val elevationLoss: Long,
    /** Distinct local dates the item was used on. */
    val days: Long,
    /** Activities of [Sport] dive; null when there are none. */
    val dives: Long? = null,
    /** Seconds of bottom time over those dives; null when there are none. */
    val bottomTime: Long? = null,
) {
    companion object {
        /** No activities counted. */
        val ZERO = GearTotals(activities = 0, movingTime = 0, distance = 0, elevationGain = 0, elevationLoss = 0, days = 0)
    }
}

/** `app.activitytracker.gear.defs#gearStarting`: the usage an item had before the athlete tracked it. */
@Immutable
@Serializable
data class GearStarting(
    /** Meters. */
    val distance: Long? = null,
    /** Seconds. */
    val time: Long? = null,
)

/** `app.activitytracker.gear.defs#gearSportTotals`: an item's totals over one activity type. */
@Immutable
@Serializable
data class GearSportTotals(
    /** A [Sport] value. */
    val sport: String,
    val totals: GearTotals,
    /** A [SubSport] value; null for the sport without one. */
    val subSport: String? = null,
)

/** `app.activitytracker.gear.defs#gearYearTotals`: an item's totals over one year of local start dates. */
@Immutable @Serializable data class GearYearTotals(val year: Int, val totals: GearTotals)

/** `app.activitytracker.gear.defs#gearUsage`: an item's usage for one viewer. */
@Immutable
@Serializable
data class GearUsage(
    /** Including [starting]. */
    val totals: GearTotals,
    /** Most used first; never includes [starting]. */
    val bySport: List<GearSportTotals>,
    /** Newest first; never includes [starting]. */
    val byYear: List<GearYearTotals>,
    /** Null when the item has none. */
    val starting: GearStarting? = null,
)

/** `app.activitytracker.gear.defs#gearComponent`: an item mounted on another, as the parent's page lists it. */
@Immutable
@Serializable
data class GearComponent(
    val gear: GearView,
    val from: Instant,
    /** Including starting usage. */
    val totals: GearTotals,
    /** Null while still mounted. */
    val to: Instant? = null,
)

/** `app.activitytracker.gear.defs#gearMountView`: one period an item was mounted on [parent], as the item's page shows it. */
@Immutable
@Serializable
data class GearMountView(
    val parent: GearView,
    val from: Instant,
    /** Null while still mounted. */
    val to: Instant? = null,
)

/** `app.activitytracker.gear.defs#actorGearItem`: one of an athlete's items, with its totals for the viewer. */
@Immutable
@Serializable
data class ActorGearItem(
    val gear: GearView,
    /** Including starting usage. */
    val totals: GearTotals,
    val retired: Boolean,
)

/** The one total the gear list shows for an item, by its [GearType] ([GearType.mainTotal]). */
enum class GearMainTotal {
    /** [GearTotals.distance]. */
    DISTANCE,

    /** [GearTotals.days] and [GearTotals.elevationLoss] (vertical). */
    DAYS_AND_VERTICAL,

    /** [GearTotals.dives]. */
    DIVES,

    /** [GearTotals.movingTime]. */
    TIME,
}

/** [GearItemRecord.type] values, an open set, and the kinds each type offers ([kinds]). */
object GearType {
    const val BIKE = "bike"
    const val COMPONENT = "component"
    const val SHOES = "shoes"
    const val SKI = "ski"
    const val SNOWBOARD = "snowboard"
    const val SKI_BOOTS = "skiBoots"
    const val SNOWBOARD_BOOTS = "snowboardBoots"
    const val XC_SKI = "xcSki"
    const val SKATES = "skates"
    const val BOARD = "board"
    const val BOAT = "boat"
    const val PADDLE = "paddle"
    const val WETSUIT = "wetsuit"
    const val DIVE_GEAR = "diveGear"
    const val HELMET = "helmet"
    const val TRAINER = "trainer"
    const val WHEELCHAIR = "wheelchair"
    const val STROLLER = "stroller"
    const val SENSOR = "sensor"
    const val DEVICE = "device"
    const val OTHER = "other"

    /** Every known type, in the order pickers offer them. */
    val all =
        listOf(
            BIKE,
            COMPONENT,
            SHOES,
            SKI,
            SNOWBOARD,
            SKI_BOOTS,
            SNOWBOARD_BOOTS,
            XC_SKI,
            SKATES,
            BOARD,
            BOAT,
            PADDLE,
            WETSUIT,
            DIVE_GEAR,
            HELMET,
            TRAINER,
            WHEELCHAIR,
            STROLLER,
            SENSOR,
            DEVICE,
            OTHER,
        )

    /** [type] if it's a known type, else [OTHER]: how an item is grouped and labeled. */
    fun known(type: String): String = if (type in all) type else OTHER

    /** The kinds [type] offers, in picker order; empty for a type with none or one this client doesn't know. */
    fun kinds(type: String): List<String> = KINDS[type].orEmpty()

    /** [kind] if [type] offers it, else null: an unknown kind shows as the type alone. */
    fun knownKind(type: String, kind: String?): String? = kind?.takeIf { it in kinds(type) }

    /** The total the gear list shows for an item of [type]; [GearMainTotal.TIME] for a type this client doesn't know. */
    fun mainTotal(type: String): GearMainTotal =
        when (type) {
            BIKE,
            COMPONENT,
            SHOES,
            WHEELCHAIR,
            STROLLER,
            SKATES -> GearMainTotal.DISTANCE
            SKI,
            SNOWBOARD,
            XC_SKI,
            SKI_BOOTS,
            SNOWBOARD_BOOTS -> GearMainTotal.DAYS_AND_VERTICAL
            DIVE_GEAR -> GearMainTotal.DIVES
            else -> GearMainTotal.TIME
        }

    private val KINDS =
        mapOf(
            BIKE to listOf("road", "gravel", "mountain", "cyclocross", "timeTrial", "eBike", "eMountainBike", "fatBike", "kids", "tandem"),
            COMPONENT to
                listOf(
                    "chain",
                    "cassette",
                    "chainring",
                    "crankset",
                    "powerMeter",
                    "tire",
                    "wheelset",
                    "brakePads",
                    "fork",
                    "shock",
                    "derailleur",
                    "saddle",
                    "pedals",
                    "battery",
                ),
            SHOES to listOf("road", "trail", "track", "racing", "walking", "hiking", "cycling", "climbing"),
            SKI to listOf("alpine", "touring", "freeride", "park", "race"),
            SNOWBOARD to listOf("allMountain", "freestyle", "freeride", "powder", "splitboard"),
            SKI_BOOTS to listOf("alpine", "touring"),
            XC_SKI to listOf("classic", "skate", "backcountry", "rollerSki"),
            SKATES to listOf("ice", "hockey", "figure", "inline", "roller"),
            BOARD to listOf("surf", "sup", "kite", "wake", "windsurf", "skate"),
            BOAT to listOf("kayak", "canoe", "rowingShell", "packraft", "sailboat"),
            PADDLE to listOf("kayak", "canoe", "sup", "oar"),
            WETSUIT to listOf("fullsuit", "springsuit", "shorty", "drysuit"),
            DIVE_GEAR to listOf("regulator", "bcd", "fins", "mask", "tank"),
            HELMET to listOf("road", "mountain", "fullFace", "timeTrial", "ski", "climbing"),
            TRAINER to listOf("smart", "wheelOn", "rollers", "treadmill", "indoorBike", "rower"),
            WHEELCHAIR to listOf("everyday", "racing", "handcycle"),
            STROLLER to listOf("running", "walking"),
            SENSOR to listOf("heartRate", "power", "speed", "cadence", "footPod", "radar"),
            DEVICE to listOf("watch", "bikeComputer", "phone", "diveComputer"),
        )
}
