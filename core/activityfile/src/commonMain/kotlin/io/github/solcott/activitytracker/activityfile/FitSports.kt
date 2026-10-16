package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport

/** A FIT `sport` and `sub_sport`, as the profile numbers them. */
data class FitSport(val sport: Int, val subSport: Int)

/**
 * Activity types to FIT's sport and sub-sport, for export: the reverse of [DecodedActivityMapper.activityType], which a test checks entry
 * by entry against Garmin's SDK names. Where FIT has several spellings of a type, this picks the one Garmin's own devices write
 * (training/yoga, cycling/indoor_cycling).
 */
object FitSports {
    /** FIT's `generic`/`generic`: a type FIT has no sport for. */
    val GENERIC = FitSport(0, 0)

    /** The type's own entry, else its sport's generic form (a sub-sport FIT lacks), else [GENERIC]. */
    fun of(type: ActivityType): FitSport = TABLE[type] ?: TABLE[ActivityType(type.sport)] ?: GENERIC

    /** Every type with an entry of its own, for the test that checks them. */
    val entries: Map<ActivityType, FitSport>
        get() = TABLE

    private fun type(sport: String, subSport: String? = null) = ActivityType(sport, subSport)

    private fun fit(sport: Int, subSport: Int) = FitSport(sport, subSport)

    private val TABLE: Map<ActivityType, FitSport> =
        mapOf(
            type(Sport.AMERICAN_FOOTBALL) to fit(9, 0), // american_football/generic
            type(Sport.ARCHERY) to fit(79, 0), // archery/generic
            type(Sport.ATV) to fit(22, 35), // motorcycling/atv
            type(Sport.BADMINTON) to fit(8, 95), // tennis/badminton
            type(Sport.BASEBALL) to fit(49, 0), // baseball/generic
            type(Sport.BASKETBALL) to fit(6, 0), // basketball/generic
            type(Sport.BOAT) to fit(23, 0), // boating/generic
            type(Sport.BOXING) to fit(47, 0), // boxing/generic
            type(Sport.BREATHWORK) to fit(10, 62), // training/breathing
            type(Sport.CANOE) to fit(88, 0), // canoeing/generic
            type(Sport.CARDIO) to fit(10, 26), // training/cardio_training
            type(Sport.CLIMB) to fit(31, 0), // rock_climbing/generic
            type(Sport.CLIMB, SubSport.BOULDERING) to fit(4, 69), // fitness_equipment/bouldering
            type(Sport.CLIMB, SubSport.INDOOR) to fit(4, 68), // fitness_equipment/indoor_climbing
            type(Sport.CRICKET) to fit(71, 0), // cricket/generic
            type(Sport.DANCE) to fit(83, 0), // dance/generic
            type(Sport.DISC_GOLF) to fit(69, 0), // disc_golf/generic
            type(Sport.DIVE) to fit(53, 0), // diving/generic
            type(Sport.DIVE, SubSport.APNEA) to fit(53, 56), // diving/apnea_diving
            type(Sport.DIVE, SubSport.APNEA_HUNT) to fit(53, 57), // diving/apnea_hunting
            type(Sport.DIVE, SubSport.CCR) to fit(53, 63), // diving/ccr_diving
            type(Sport.DIVE, SubSport.GAUGE) to fit(53, 55), // diving/gauge_diving
            type(Sport.DIVE, SubSport.MULTI_GAS) to fit(53, 54), // diving/multi_gas_diving
            type(Sport.DIVE, SubSport.SINGLE_GAS) to fit(53, 53), // diving/single_gas_diving
            type(Sport.ELLIPTICAL) to fit(4, 15), // fitness_equipment/elliptical
            type(Sport.FISH) to fit(29, 0), // fishing/generic
            type(Sport.FLOOR_CLIMB) to fit(48, 0), // floor_climbing/generic
            type(Sport.FLYING) to fit(20, 0), // flying/generic
            type(Sport.GOLF) to fit(25, 0), // golf/generic
            type(Sport.HANDCYCLE) to fit(2, 12), // cycling/hand_cycling
            type(Sport.HANDCYCLE, SubSport.INDOOR) to fit(2, 88), // cycling/indoor_hand_cycling
            type(Sport.HIIT) to fit(62, 0), // hiit/generic
            type(Sport.HIKE) to fit(17, 0), // hiking/generic
            type(Sport.HORSEBACK) to fit(27, 0), // horseback_riding/generic
            type(Sport.HUNT) to fit(28, 0), // hunting/generic
            type(Sport.ICE_HOCKEY) to fit(73, 0), // hockey/generic
            type(Sport.ICE_SKATE) to fit(33, 0), // ice_skating/generic
            type(Sport.INLINE_SKATE) to fit(30, 0), // inline_skating/generic
            type(Sport.JUMP_ROPE) to fit(84, 0), // jump_rope/generic
            type(Sport.KAYAK) to fit(19, 0), // paddling/generic
            type(Sport.KITEBOARD) to fit(44, 0), // kitesurfing/generic
            type(Sport.LACROSSE) to fit(74, 0), // lacrosse/generic
            type(Sport.MEDITATION) to fit(67, 0), // meditation/generic
            type(Sport.MMA) to fit(80, 0), // mixed_martial_arts/generic
            type(Sport.MOBILITY) to fit(86, 0), // mobility/generic
            type(Sport.MOTOCROSS) to fit(22, 36), // motorcycling/motocross
            type(Sport.MOTORCYCLE) to fit(22, 0), // motorcycling/generic
            type(Sport.MOUNTAINEERING) to fit(16, 0), // mountaineering/generic
            type(Sport.MULTISPORT) to fit(18, 0), // multisport/generic
            type(Sport.MULTISPORT, SubSport.TRIATHLON) to fit(18, 78), // multisport/triathlon
            type(Sport.OTHER) to fit(0, 0), // generic/generic
            type(Sport.PADEL) to fit(8, 85), // tennis/padel
            type(Sport.PICKLEBALL) to fit(8, 84), // tennis/pickleball
            type(Sport.PILATES) to fit(10, 44), // training/pilates
            type(Sport.RACQUETBALL) to fit(8, 96), // tennis/racquetball
            type(Sport.RIDE) to fit(2, 0), // cycling/generic
            type(Sport.RIDE, SubSport.BMX) to fit(2, 29), // cycling/bmx
            type(Sport.RIDE, SubSport.COMMUTE) to fit(2, 48), // cycling/commuting
            type(Sport.RIDE, SubSport.CYCLOCROSS) to fit(2, 11), // cycling/cyclocross
            type(Sport.RIDE, SubSport.E_BIKE) to fit(21, 0), // e_biking/generic
            type(Sport.RIDE, SubSport.E_MOUNTAIN) to fit(2, 47), // cycling/e_bike_mountain
            type(Sport.RIDE, SubSport.GRAVEL) to fit(2, 46), // cycling/gravel_cycling
            type(Sport.RIDE, SubSport.INDOOR) to fit(2, 6), // cycling/indoor_cycling
            type(Sport.RIDE, SubSport.MOUNTAIN) to fit(2, 8), // cycling/mountain
            type(Sport.RIDE, SubSport.ROAD) to fit(2, 7), // cycling/road
            type(Sport.RIDE, SubSport.VIRTUAL) to fit(2, 58), // cycling/virtual_activity
            type(Sport.ROW) to fit(15, 0), // rowing/generic
            type(Sport.ROW, SubSport.INDOOR) to fit(4, 14), // fitness_equipment/indoor_rowing
            type(Sport.ROW, SubSport.VIRTUAL) to fit(15, 58), // rowing/virtual_activity
            type(Sport.RUGBY) to fit(72, 0), // rugby/generic
            type(Sport.RUN) to fit(1, 0), // running/generic
            type(Sport.RUN, SubSport.INDOOR_TRACK) to fit(1, 45), // running/indoor_running
            type(Sport.RUN, SubSport.OBSTACLE) to fit(1, 59), // running/obstacle
            type(Sport.RUN, SubSport.TRACK) to fit(1, 4), // running/track
            type(Sport.RUN, SubSport.TRAIL) to fit(1, 3), // running/trail
            type(Sport.RUN, SubSport.TREADMILL) to fit(1, 1), // running/treadmill
            type(Sport.RUN, SubSport.ULTRA) to fit(1, 67), // running/ultra
            type(Sport.RUN, SubSport.VIRTUAL) to fit(1, 58), // running/virtual_activity
            type(Sport.SAIL) to fit(32, 0), // sailing/generic
            type(Sport.SAIL, SubSport.RACE) to fit(32, 65), // sailing/sail_race
            type(Sport.SKI) to fit(13, 0), // alpine_skiing/generic
            type(Sport.SKI, SubSport.BACKCOUNTRY) to fit(13, 37), // alpine_skiing/backcountry
            type(Sport.SKYDIVE) to fit(34, 0), // sky_diving/generic
            type(Sport.SNORKEL) to fit(82, 0), // snorkeling/generic
            type(Sport.SNOWBOARD) to fit(14, 0), // snowboarding/generic
            type(Sport.SNOWBOARD, SubSport.BACKCOUNTRY) to fit(14, 37), // snowboarding/backcountry
            type(Sport.SNOWMOBILE) to fit(36, 0), // snowmobiling/generic
            type(Sport.SNOWSHOE) to fit(35, 0), // snowshoeing/generic
            type(Sport.SOCCER) to fit(7, 0), // soccer/generic
            type(Sport.SQUASH) to fit(8, 94), // tennis/squash
            type(Sport.STAIR_STEPPER) to fit(4, 16), // fitness_equipment/stair_climbing
            type(Sport.STRENGTH) to fit(10, 20), // training/strength_training
            type(Sport.SUP) to fit(37, 0), // stand_up_paddleboarding/generic
            type(Sport.SURF) to fit(38, 0), // surfing/generic
            type(Sport.SWIM) to fit(5, 0), // swimming/generic
            type(Sport.SWIM, SubSport.OPEN_WATER) to fit(5, 18), // swimming/open_water
            type(Sport.SWIM, SubSport.POOL) to fit(5, 17), // swimming/lap_swimming
            type(Sport.TABLE_TENNIS) to fit(8, 97), // tennis/table_tennis
            type(Sport.TENNIS) to fit(8, 0), // tennis/generic
            type(Sport.TUBE) to fit(76, 0), // water_tubing/generic
            type(Sport.VOLLEYBALL) to fit(75, 0), // volleyball/generic
            type(Sport.WAKEBOARD) to fit(39, 0), // wakeboarding/generic
            type(Sport.WAKESURF) to fit(77, 0), // wakesurfing/generic
            type(Sport.WALK) to fit(11, 0), // walking/generic
            type(Sport.WALK, SubSport.INDOOR) to fit(4, 27), // fitness_equipment/indoor_walking
            type(Sport.WALK, SubSport.TREADMILL) to fit(11, 1), // walking/treadmill
            type(Sport.WATER_SKI) to fit(40, 0), // water_skiing/generic
            type(Sport.WHEELCHAIR, SubSport.RUN_PACE) to fit(66, 0), // wheelchair_push_run/generic
            type(Sport.WHEELCHAIR, SubSport.WALK_PACE) to fit(65, 0), // wheelchair_push_walk/generic
            type(Sport.WHITEWATER) to fit(42, 0), // rafting/generic
            type(Sport.WINDSURF) to fit(43, 0), // windsurfing/generic
            type(Sport.XC_SKI) to fit(12, 0), // cross_country_skiing/generic
            type(Sport.XC_SKI, SubSport.INDOOR) to fit(4, 25), // fitness_equipment/indoor_skiing
            type(Sport.XC_SKI, SubSport.SKATE) to fit(12, 42), // cross_country_skiing/skate_skiing
            type(Sport.YOGA) to fit(10, 43), // training/yoga
        )
}
