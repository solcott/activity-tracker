package io.github.solcott.activitytracker.app.icon

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport

/** Each sport's image. */
private val SPORTS: Map<String, String> =
    mapOf(
        Sport.RUN to "directions_run",
        Sport.RIDE to "directions_bike",
        Sport.HANDCYCLE to "accessible_forward",
        Sport.SWIM to "pool",
        Sport.MULTISPORT to "sports_score",
        Sport.WATER_FITNESS to "pool",
        Sport.WALK to "directions_walk",
        Sport.HIKE to "hiking",
        Sport.RUCKING to "backpack",
        Sport.MOUNTAINEERING to "mountain_flag",
        Sport.CLIMB to "altitude",
        Sport.INLINE_SKATE to "roller_skating",
        Sport.HORSEBACK to "explore",
        Sport.GOLF to "sports_golf",
        Sport.DISC_GOLF to "album",
        Sport.ARCHERY to "target",
        Sport.HUNT to "forest",
        Sport.FISH to "phishing",
        Sport.ADVENTURE_RACE to "explore",
        Sport.EXPEDITION to "camping",
        Sport.WHEELCHAIR to "accessible",
        Sport.SKATEBOARD to "skateboarding",
        Sport.KICK_SCOOTER to "scooter",
        Sport.ORIENTEERING to "map",
        Sport.PARKOUR to "sprint",
        Sport.SKI to "downhill_skiing",
        Sport.SNOWBOARD to "snowboarding",
        Sport.XC_SKI to "nordic_walking",
        Sport.SNOWSHOE to "snowshoeing",
        Sport.ICE_SKATE to "ice_skating",
        Sport.CURLING to "severe_cold",
        Sport.ROW to "rowing",
        Sport.KAYAK to "kayaking",
        Sport.CANOE to "kayaking",
        Sport.SUP to "surfing",
        Sport.SURF to "surfing",
        Sport.KITEBOARD to "kitesurfing",
        Sport.WINDSURF to "sailing",
        Sport.SAIL to "sailing",
        Sport.WAKEBOARD to "surfing",
        Sport.WAKESURF to "surfing",
        Sport.WATER_SKI to "waves",
        Sport.WHITEWATER to "kayaking",
        Sport.TUBE to "waves",
        Sport.SNORKEL to "scuba_diving",
        Sport.BOAT to "directions_boat",
        Sport.DIVE to "scuba_diving",
        Sport.STRENGTH to "fitness_center",
        Sport.CARDIO to "monitor_heart",
        Sport.HIIT to "local_fire_department",
        Sport.YOGA to "self_improvement",
        Sport.PILATES to "accessibility_new",
        Sport.MOBILITY to "accessibility_new",
        Sport.ELLIPTICAL to "exercise",
        Sport.STAIR_STEPPER to "stairs_2",
        Sport.FLOOR_CLIMB to "stairs",
        Sport.JUMP_ROPE to "exercise",
        Sport.BOXING to "sports_mma",
        Sport.MMA to "sports_martial_arts",
        Sport.WRESTLING to "sports_kabaddi",
        Sport.FENCING to "swords",
        Sport.TAI_CHI to "sports_martial_arts",
        Sport.DANCE to "music_note",
        Sport.GYMNASTICS to "sports_gymnastics",
        Sport.RECOVERY to "hot_tub",
        Sport.SOCCER to "sports_soccer",
        Sport.AMERICAN_FOOTBALL to "sports_football",
        Sport.BASEBALL to "sports_baseball",
        Sport.SOFTBALL to "sports_baseball",
        Sport.BASKETBALL to "sports_basketball",
        Sport.VOLLEYBALL to "sports_volleyball",
        Sport.ICE_HOCKEY to "sports_hockey",
        Sport.FIELD_HOCKEY to "sports_hockey",
        Sport.LACROSSE to "sports_hockey",
        Sport.RUGBY to "sports_rugby",
        Sport.CRICKET to "sports_cricket",
        Sport.ULTIMATE to "album",
        Sport.HANDBALL to "sports_handball",
        Sport.WATER_POLO to "pool",
        Sport.AUSTRALIAN_FOOTBALL to "sports_rugby",
        Sport.ROLLER_HOCKEY to "sports_hockey",
        Sport.FLOORBALL to "sports_hockey",
        Sport.TENNIS to "sports_tennis",
        Sport.PICKLEBALL to "sports_tennis",
        Sport.PADEL to "sports_tennis",
        Sport.BADMINTON to "badminton",
        Sport.SQUASH to "sports_tennis",
        Sport.RACQUETBALL to "sports_tennis",
        Sport.TABLE_TENNIS to "sports_tennis",
        Sport.PLATFORM_TENNIS to "sports_tennis",
        Sport.MOTORCYCLE to "two_wheeler",
        Sport.MOTOCROSS to "sports_motorsports",
        Sport.ATV to "sports_motorsports",
        Sport.SNOWMOBILE to "snowmobile",
        Sport.OVERLAND to "directions_car",
        Sport.MEDITATION to "spa",
        Sport.BREATHWORK to "air",
        Sport.FLYING to "flight",
        Sport.SKYDIVE to "paragliding",
        Sport.GAMING to "sports_esports",
        Sport.BOWLING to "trip_origin",
        Sport.PLAY to "playground",
        Sport.YARD_WORK to "yard",
        Sport.HOUSEWORK to "cleaning_services",
        Sport.OTHER to "sports_and_outdoors",
    )

/** Sub-sports that look different enough from their sport to get their own image. */
private val SUB_SPORTS: Map<ActivityType, String> =
    mapOf(
        ActivityType(Sport.RIDE, SubSport.E_BIKE) to "electric_bike",
        ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN) to "electric_bike",
        ActivityType(Sport.SWIM, SubSport.OPEN_WATER) to "waves",
        ActivityType(Sport.WALK, SubSport.NORDIC) to "nordic_walking",
        ActivityType(Sport.WHEELCHAIR, SubSport.RUN_PACE) to "accessible_forward",
        ActivityType(Sport.FLYING, SubSport.PARAGLIDING) to "paragliding",
        ActivityType(Sport.WALK, SubSport.DOG) to "pets",
        ActivityType(Sport.WALK, SubSport.STROLLER) to "stroller",
        ActivityType(Sport.RUN, SubSport.STROLLER) to "stroller",
        ActivityType(Sport.RECOVERY, SubSport.SAUNA) to "sauna",
        ActivityType(Sport.RECOVERY, SubSport.COLD_PLUNGE) to "severe_cold",
    )

/**
 * Which image each sport draws, by the image's name. The images are Material Symbols (Outlined, 24px, Apache 2.0) from
 * google/material-design-icons, vendored into `:ui-resources`' `composeResources/drawable` under their symbol names; the shared UI finds
 * one there by name and the native iOS UI in the asset catalog generated from the same files (dev/ios-native-resources). Several sports
 * share a symbol where Material has none of their own (every racket sport is a tennis racket).
 */
object SportIconNames {
    /** The image for a sport another client added, which the app has none for. */
    const val GENERIC = "sports_and_outdoors"

    /** Every image a sport can draw, for the test that each exists. */
    val all: Set<String>
        get() = SPORTS.values.toSet() + SUB_SPORTS.values + GENERIC

    /** Whether [sport] has an image of its own, not the generic one. */
    fun names(sport: String): Boolean = sport in SPORTS

    /** The image for [type]: its sub-sport's, else its sport's, else the generic one. */
    fun of(type: ActivityType): String = SUB_SPORTS[type] ?: SPORTS[type.sport] ?: GENERIC
}
