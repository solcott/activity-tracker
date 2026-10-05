package io.github.solcott.activitytracker.data.sport

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport

/** The groups the sport picker lists activity types under. They follow Garmin's activity menus. Never stored. */
enum class SportCategory {
    Running,
    Cycling,
    Swimming,
    Multisport,
    Outdoor,
    WinterSports,
    WaterSports,
    Diving,
    Gym,
    TeamSports,
    RacketSports,
    Motorsports,
    Other,
}

/** How an activity's average rate reads. */
enum class RateKind {
    /** Time per kilometer or mile. */
    Pace,

    /** Time per 100 meters or yards. */
    SwimPace,

    /** Kilometers or miles per hour. */
    Speed,

    /** No rate: distance means little for this sport. */
    None,
}

/** A [SubSport] of some sport. [indoor] types can't use GPS. */
@Immutable data class SubSportInfo(val subSport: String, val indoor: Boolean = false)

/** A known [Sport]: where the picker lists it, how its rate reads, and its sub-sports. [indoor] sports can't use GPS. */
@Immutable
data class SportInfo(
    val sport: String,
    val category: SportCategory,
    val rate: RateKind,
    val subSports: List<SubSportInfo> = emptyList(),
    val indoor: Boolean = false,
)

/** Every activity type in one picker group, each sport followed by its sub-sports. */
@Immutable data class CategoryTypes(val category: SportCategory, val types: List<ActivityType>)

/**
 * The known activity types, modeled on Garmin's activity profiles. Sport and sub-sport tokens are open strings in the lexicon, so a type
 * this catalog doesn't know (from another client) still displays, falling back to [RateKind.Pace] like the original sports did.
 */
object SportCatalog {
    val sports: List<SportInfo> = buildList {
        fun plain(category: SportCategory, rate: RateKind, vararg sports: String, indoor: Boolean = false) = sports.forEach {
            add(SportInfo(it, category, rate, indoor = indoor))
        }

        fun withSubs(category: SportCategory, rate: RateKind, sport: String, vararg subSports: SubSportInfo) =
            add(SportInfo(sport, category, rate, subSports.toList()))

        withSubs(
            SportCategory.Running,
            RateKind.Pace,
            Sport.RUN,
            sub(SubSport.TRAIL),
            sub(SubSport.TREADMILL, indoor = true),
            sub(SubSport.TRACK),
            sub(SubSport.INDOOR_TRACK, indoor = true),
            sub(SubSport.VIRTUAL, indoor = true),
            sub(SubSport.ULTRA),
            sub(SubSport.OBSTACLE),
        )
        withSubs(
            SportCategory.Cycling,
            RateKind.Speed,
            Sport.RIDE,
            sub(SubSport.ROAD),
            sub(SubSport.MOUNTAIN),
            sub(SubSport.GRAVEL),
            sub(SubSport.CYCLOCROSS),
            sub(SubSport.INDOOR, indoor = true),
            sub(SubSport.VIRTUAL, indoor = true),
            sub(SubSport.E_BIKE),
            sub(SubSport.E_MOUNTAIN),
            sub(SubSport.COMMUTE),
            sub(SubSport.TOUR),
            sub(SubSport.BMX),
        )
        withSubs(SportCategory.Cycling, RateKind.Speed, Sport.HANDCYCLE, sub(SubSport.INDOOR, indoor = true))
        withSubs(SportCategory.Swimming, RateKind.SwimPace, Sport.SWIM, sub(SubSport.POOL, indoor = true), sub(SubSport.OPEN_WATER))
        withSubs(
            SportCategory.Multisport,
            RateKind.Speed,
            Sport.MULTISPORT,
            sub(SubSport.TRIATHLON),
            sub(SubSport.DUATHLON),
            sub(SubSport.SWIMRUN),
            sub(SubSport.BRICK),
            sub(SubSport.MIXED),
        )

        withSubs(
            SportCategory.Outdoor,
            RateKind.Pace,
            Sport.WALK,
            sub(SubSport.INDOOR, indoor = true),
            sub(SubSport.TREADMILL, indoor = true),
        )
        plain(SportCategory.Outdoor, RateKind.Pace, Sport.HIKE, Sport.RUCKING, Sport.MOUNTAINEERING)
        withSubs(SportCategory.Outdoor, RateKind.None, Sport.CLIMB, sub(SubSport.BOULDERING), sub(SubSport.INDOOR, indoor = true))
        plain(SportCategory.Outdoor, RateKind.Speed, Sport.INLINE_SKATE, Sport.HORSEBACK)
        plain(SportCategory.Outdoor, RateKind.None, Sport.GOLF, Sport.DISC_GOLF, Sport.ARCHERY, Sport.HUNT, Sport.FISH)
        plain(SportCategory.Outdoor, RateKind.Pace, Sport.ADVENTURE_RACE)
        plain(SportCategory.Outdoor, RateKind.Speed, Sport.EXPEDITION)

        withSubs(SportCategory.WinterSports, RateKind.Speed, Sport.SKI, sub(SubSport.BACKCOUNTRY))
        withSubs(SportCategory.WinterSports, RateKind.Speed, Sport.SNOWBOARD, sub(SubSport.BACKCOUNTRY))
        withSubs(
            SportCategory.WinterSports,
            RateKind.Speed,
            Sport.XC_SKI,
            sub(SubSport.CLASSIC),
            sub(SubSport.SKATE),
            sub(SubSport.INDOOR, indoor = true),
        )
        plain(SportCategory.WinterSports, RateKind.Pace, Sport.SNOWSHOE)
        plain(SportCategory.WinterSports, RateKind.Speed, Sport.ICE_SKATE)

        withSubs(SportCategory.WaterSports, RateKind.Speed, Sport.ROW, sub(SubSport.INDOOR, indoor = true))
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.KAYAK, Sport.SUP)
        plain(SportCategory.WaterSports, RateKind.None, Sport.SURF)
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.KITEBOARD, Sport.WINDSURF)
        withSubs(SportCategory.WaterSports, RateKind.Speed, Sport.SAIL, sub(SubSport.RACE), sub(SubSport.EXPEDITION))
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.WAKEBOARD, Sport.WAKESURF, Sport.WATER_SKI, Sport.WHITEWATER)
        plain(SportCategory.WaterSports, RateKind.None, Sport.TUBE, Sport.SNORKEL)
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.BOAT)

        add(
            SportInfo(
                Sport.DIVE,
                SportCategory.Diving,
                RateKind.None,
                listOf(
                    sub(SubSport.SINGLE_GAS),
                    sub(SubSport.MULTI_GAS),
                    sub(SubSport.CCR),
                    sub(SubSport.GAUGE),
                    sub(SubSport.APNEA),
                    sub(SubSport.APNEA_HUNT),
                    sub(SubSport.POOL_APNEA),
                ),
                indoor = true,
            )
        )

        plain(
            SportCategory.Gym,
            RateKind.None,
            Sport.STRENGTH,
            Sport.CARDIO,
            Sport.HIIT,
            Sport.YOGA,
            Sport.PILATES,
            Sport.MOBILITY,
            Sport.ELLIPTICAL,
            Sport.STAIR_STEPPER,
            Sport.FLOOR_CLIMB,
            Sport.JUMP_ROPE,
            Sport.BOXING,
            Sport.MMA,
            Sport.DANCE,
            Sport.GYMNASTICS,
            indoor = true,
        )

        plain(
            SportCategory.TeamSports,
            RateKind.None,
            Sport.SOCCER,
            Sport.AMERICAN_FOOTBALL,
            Sport.BASEBALL,
            Sport.SOFTBALL,
            Sport.BASKETBALL,
            Sport.VOLLEYBALL,
            Sport.ICE_HOCKEY,
            Sport.FIELD_HOCKEY,
            Sport.LACROSSE,
            Sport.RUGBY,
            Sport.CRICKET,
            Sport.ULTIMATE,
        )
        plain(
            SportCategory.RacketSports,
            RateKind.None,
            Sport.TENNIS,
            Sport.PICKLEBALL,
            Sport.PADEL,
            Sport.BADMINTON,
            Sport.SQUASH,
            Sport.RACQUETBALL,
            Sport.TABLE_TENNIS,
            Sport.PLATFORM_TENNIS,
        )
        plain(SportCategory.Motorsports, RateKind.Speed, Sport.MOTORCYCLE, Sport.MOTOCROSS, Sport.ATV, Sport.SNOWMOBILE, Sport.OVERLAND)

        plain(SportCategory.Other, RateKind.None, Sport.MEDITATION, Sport.BREATHWORK, Sport.GAMING, indoor = true)
        plain(SportCategory.Other, RateKind.Speed, Sport.FLYING, Sport.SKYDIVE)
        plain(SportCategory.Other, RateKind.Pace, Sport.OTHER)
    }

    /** Sport and sub-sport pairs a second group also lists, as Garmin lists indoor profiles under Gym too. */
    private val shortcuts: Map<SportCategory, List<ActivityType>> =
        mapOf(
            SportCategory.Gym to
                listOf(
                    ActivityType(Sport.RUN, SubSport.TREADMILL),
                    ActivityType(Sport.RUN, SubSport.INDOOR_TRACK),
                    ActivityType(Sport.RIDE, SubSport.INDOOR),
                    ActivityType(Sport.WALK, SubSport.INDOOR),
                    ActivityType(Sport.ROW, SubSport.INDOOR),
                    ActivityType(Sport.XC_SKI, SubSport.INDOOR),
                    ActivityType(Sport.CLIMB, SubSport.INDOOR),
                    ActivityType(Sport.CLIMB, SubSport.BOULDERING),
                ),
            SportCategory.Running to listOf(ActivityType(Sport.WALK)),
            SportCategory.Diving to listOf(ActivityType(Sport.SNORKEL)),
        )

    private val bySport: Map<String, SportInfo> = sports.associateBy { it.sport }

    /** The picker's groups in order, each listing its sports (followed by their sub-sports) and then its shortcuts. */
    val categories: List<CategoryTypes> =
        SportCategory.entries.map { category ->
            val own =
                sports
                    .filter { it.category == category }
                    .flatMap { info -> listOf(ActivityType(info.sport)) + info.subSports.map { ActivityType(info.sport, it.subSport) } }
            CategoryTypes(category, own + shortcuts[category].orEmpty())
        }

    /** What the picker offers before anything has been tracked. */
    val common: List<ActivityType> =
        listOf(Sport.RUN, Sport.RIDE, Sport.WALK, Sport.HIKE, Sport.SWIM, Sport.STRENGTH).map { ActivityType(it) }

    fun info(sport: String): SportInfo? = bySport[sport]

    fun subSports(sport: String): List<SubSportInfo> = info(sport)?.subSports.orEmpty()

    fun rate(sport: String): RateKind = info(sport)?.rate ?: RateKind.Pace

    /** Whether the type is known. A sub-sport must belong to its sport. */
    fun isKnown(type: ActivityType): Boolean =
        info(type.sport)?.let { info -> type.subSport == null || info.subSports.any { it.subSport == type.subSport } } == true

    /** Whether the type can't use GPS (a treadmill run, a strength session), so GPS recording doesn't offer it. */
    fun isIndoor(type: ActivityType): Boolean {
        val info = info(type.sport) ?: return false
        return info.indoor || info.subSports.any { it.subSport == type.subSport && it.indoor }
    }

    private fun sub(subSport: String, indoor: Boolean = false) = SubSportInfo(subSport, indoor)
}
