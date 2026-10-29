package io.github.solcott.activitytracker.data.sport

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport

/**
 * The groups the sport picker lists activity types under, in order. They follow Garmin Connect's activity list, plus groups for what it
 * doesn't track. Never stored.
 */
enum class SportCategory {
    Running,
    Cycling,
    Gym,
    MindBody,
    Combat,
    Swimming,
    Multisport,
    Walking,
    HikingClimbing,
    WinterSports,
    Parasports,
    WaterSports,
    Diving,
    TeamSports,
    RacketSports,
    Target,
    Skating,
    Adventure,
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

/** A [SubSport] of some sport. [indoor] types can't use GPS. [category] files it under another picker group than its sport's. */
@Immutable data class SubSportInfo(val subSport: String, val indoor: Boolean = false, val category: SportCategory? = null)

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
 * The known activity types: every FIT activity profile, so FIT files import exactly, plus the types Apple Health, Health Connect and Strava
 * exports use. Sport and sub-sport tokens are open strings in the lexicon, so a type this catalog doesn't know (from another client) still
 * displays, falling back to [RateKind.Pace] like the original sports did.
 */
object SportCatalog {
    val sports: List<SportInfo> = buildList {
        fun plain(category: SportCategory, rate: RateKind, vararg sports: String, indoor: Boolean = false) = sports.forEach {
            add(SportInfo(it, category, rate, indoor = indoor))
        }

        fun withSubs(category: SportCategory, rate: RateKind, sport: String, vararg subSports: SubSportInfo) =
            add(SportInfo(sport, category, rate, subSports.toList()))

        /** An indoor sport with no rate, as most gym and studio sports are. */
        fun studio(category: SportCategory, sport: String, vararg subSports: SubSportInfo) =
            add(SportInfo(sport, category, RateKind.None, subSports.toList(), indoor = true))

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
            sub(SubSport.STROLLER),
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
            sub(SubSport.VELOMOBILE),
        )
        withSubs(SportCategory.Cycling, RateKind.Speed, Sport.HANDCYCLE, sub(SubSport.INDOOR, indoor = true))

        studio(
            SportCategory.Gym,
            Sport.STRENGTH,
            sub(SubSport.FUNCTIONAL),
            sub(SubSport.CROSSFIT),
            sub(SubSport.CALISTHENICS),
            sub(SubSport.CORE),
        )
        studio(SportCategory.Gym, Sport.CARDIO, sub(SubSport.STEP))
        studio(SportCategory.Gym, Sport.HIIT, sub(SubSport.BOOTCAMP), sub(SubSport.CIRCUIT))
        plain(
            SportCategory.Gym,
            RateKind.None,
            Sport.ELLIPTICAL,
            Sport.STAIR_STEPPER,
            Sport.FLOOR_CLIMB,
            Sport.JUMP_ROPE,
            indoor = true,
        )
        studio(SportCategory.Gym, Sport.DANCE, sub(SubSport.CARDIO), sub(SubSport.SOCIAL))
        studio(SportCategory.Gym, Sport.GYMNASTICS)
        plain(SportCategory.Gym, RateKind.None, Sport.PARKOUR)

        studio(SportCategory.MindBody, Sport.YOGA)
        studio(SportCategory.MindBody, Sport.PILATES, sub(SubSport.BARRE))
        plain(SportCategory.MindBody, RateKind.None, Sport.TAI_CHI, Sport.MEDITATION, Sport.BREATHWORK, indoor = true)
        studio(SportCategory.MindBody, Sport.MOBILITY, sub(SubSport.STRETCHING), sub(SubSport.COOLDOWN))
        studio(SportCategory.MindBody, Sport.RECOVERY, sub(SubSport.SAUNA), sub(SubSport.COLD_PLUNGE))

        studio(SportCategory.Combat, Sport.BOXING, sub(SubSport.KICKBOXING))
        plain(SportCategory.Combat, RateKind.None, Sport.MMA, Sport.WRESTLING, Sport.FENCING, indoor = true)

        withSubs(SportCategory.Swimming, RateKind.SwimPace, Sport.SWIM, sub(SubSport.POOL, indoor = true), sub(SubSport.OPEN_WATER))
        plain(SportCategory.Swimming, RateKind.None, Sport.WATER_FITNESS, indoor = true)

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
            SportCategory.Walking,
            RateKind.Pace,
            Sport.WALK,
            sub(SubSport.INDOOR, indoor = true),
            sub(SubSport.TREADMILL, indoor = true),
            sub(SubSport.NORDIC),
            sub(SubSport.DOG),
            sub(SubSport.STROLLER),
        )

        plain(SportCategory.HikingClimbing, RateKind.Pace, Sport.HIKE, Sport.RUCKING, Sport.MOUNTAINEERING)
        withSubs(SportCategory.HikingClimbing, RateKind.None, Sport.CLIMB, sub(SubSport.BOULDERING), sub(SubSport.INDOOR, indoor = true))

        withSubs(SportCategory.WinterSports, RateKind.Speed, Sport.SKI, sub(SubSport.BACKCOUNTRY))
        withSubs(SportCategory.WinterSports, RateKind.Speed, Sport.SNOWBOARD, sub(SubSport.BACKCOUNTRY))
        withSubs(
            SportCategory.WinterSports,
            RateKind.Speed,
            Sport.XC_SKI,
            sub(SubSport.CLASSIC),
            sub(SubSport.SKATE),
            sub(SubSport.ROLLER),
            sub(SubSport.INDOOR, indoor = true, category = SportCategory.Gym),
        )
        plain(SportCategory.WinterSports, RateKind.Pace, Sport.SNOWSHOE)
        plain(SportCategory.WinterSports, RateKind.Speed, Sport.ICE_SKATE)
        plain(SportCategory.WinterSports, RateKind.None, Sport.CURLING, indoor = true)
        plain(SportCategory.WinterSports, RateKind.Speed, Sport.SNOWMOBILE)

        withSubs(SportCategory.Parasports, RateKind.Pace, Sport.WHEELCHAIR, sub(SubSport.WALK_PACE), sub(SubSport.RUN_PACE))

        withSubs(
            SportCategory.WaterSports,
            RateKind.Speed,
            Sport.ROW,
            sub(SubSport.INDOOR, indoor = true, category = SportCategory.Gym),
            sub(SubSport.VIRTUAL, indoor = true, category = SportCategory.Gym),
        )
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.KAYAK, Sport.CANOE, Sport.SUP)
        plain(SportCategory.WaterSports, RateKind.None, Sport.SURF)
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.KITEBOARD, Sport.WINDSURF)
        withSubs(SportCategory.WaterSports, RateKind.Speed, Sport.SAIL, sub(SubSport.RACE), sub(SubSport.EXPEDITION))
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.WAKEBOARD, Sport.WAKESURF, Sport.WATER_SKI, Sport.WHITEWATER)
        plain(SportCategory.WaterSports, RateKind.None, Sport.TUBE, Sport.SNORKEL)
        plain(SportCategory.WaterSports, RateKind.Speed, Sport.BOAT)
        plain(SportCategory.WaterSports, RateKind.None, Sport.FISH)

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

        withSubs(SportCategory.TeamSports, RateKind.None, Sport.SOCCER, sub(SubSport.FUTSAL, indoor = true))
        plain(SportCategory.TeamSports, RateKind.None, Sport.AMERICAN_FOOTBALL, Sport.BASEBALL, Sport.SOFTBALL, Sport.BASKETBALL)
        withSubs(SportCategory.TeamSports, RateKind.None, Sport.VOLLEYBALL, sub(SubSport.BEACH))
        plain(
            SportCategory.TeamSports,
            RateKind.None,
            Sport.ICE_HOCKEY,
            Sport.FIELD_HOCKEY,
            Sport.LACROSSE,
            Sport.RUGBY,
            Sport.CRICKET,
            Sport.ULTIMATE,
            Sport.HANDBALL,
            Sport.WATER_POLO,
            Sport.AUSTRALIAN_FOOTBALL,
            Sport.ROLLER_HOCKEY,
            Sport.FLOORBALL,
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

        plain(SportCategory.Target, RateKind.None, Sport.GOLF, Sport.DISC_GOLF, Sport.ARCHERY)
        plain(SportCategory.Target, RateKind.None, Sport.BOWLING, indoor = true)

        plain(SportCategory.Skating, RateKind.Speed, Sport.INLINE_SKATE, Sport.SKATEBOARD, Sport.KICK_SCOOTER)

        plain(SportCategory.Adventure, RateKind.Pace, Sport.ADVENTURE_RACE, Sport.ORIENTEERING)
        plain(SportCategory.Adventure, RateKind.Speed, Sport.EXPEDITION, Sport.HORSEBACK)
        plain(SportCategory.Adventure, RateKind.None, Sport.HUNT)
        withSubs(SportCategory.Adventure, RateKind.Speed, Sport.FLYING, sub(SubSport.PARAGLIDING))
        plain(SportCategory.Adventure, RateKind.Speed, Sport.SKYDIVE)

        plain(SportCategory.Motorsports, RateKind.Speed, Sport.MOTORCYCLE, Sport.MOTOCROSS, Sport.ATV, Sport.OVERLAND)

        plain(SportCategory.Other, RateKind.None, Sport.GAMING, Sport.HOUSEWORK, indoor = true)
        plain(SportCategory.Other, RateKind.None, Sport.PLAY, Sport.YARD_WORK)
        plain(SportCategory.Other, RateKind.Pace, Sport.OTHER)
    }

    private val bySport: Map<String, SportInfo> = sports.associateBy { it.sport }

    /**
     * The picker's groups in order. Each type is listed once: a sport in its category, followed by its sub-sports there, and a sub-sport
     * filed under another category in that one instead.
     */
    val categories: List<CategoryTypes> =
        sports
            .flatMap { info ->
                listOf(info.category to ActivityType(info.sport)) +
                    info.subSports.map { (it.category ?: info.category) to ActivityType(info.sport, it.subSport) }
            }
            .groupBy({ it.first }, { it.second })
            .let { byCategory -> SportCategory.entries.map { CategoryTypes(it, byCategory[it].orEmpty()) } }

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

    private fun sub(subSport: String, indoor: Boolean = false, category: SportCategory? = null) = SubSportInfo(subSport, indoor, category)
}
