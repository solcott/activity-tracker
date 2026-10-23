package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
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
    }
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
