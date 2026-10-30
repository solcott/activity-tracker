package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.accessibility_new
import io.github.solcott.activitytracker.composeApp.resources.accessible
import io.github.solcott.activitytracker.composeApp.resources.accessible_forward
import io.github.solcott.activitytracker.composeApp.resources.air
import io.github.solcott.activitytracker.composeApp.resources.album
import io.github.solcott.activitytracker.composeApp.resources.altitude
import io.github.solcott.activitytracker.composeApp.resources.backpack
import io.github.solcott.activitytracker.composeApp.resources.badminton
import io.github.solcott.activitytracker.composeApp.resources.camping
import io.github.solcott.activitytracker.composeApp.resources.cleaning_services
import io.github.solcott.activitytracker.composeApp.resources.directions_bike
import io.github.solcott.activitytracker.composeApp.resources.directions_boat
import io.github.solcott.activitytracker.composeApp.resources.directions_car
import io.github.solcott.activitytracker.composeApp.resources.directions_run
import io.github.solcott.activitytracker.composeApp.resources.directions_walk
import io.github.solcott.activitytracker.composeApp.resources.downhill_skiing
import io.github.solcott.activitytracker.composeApp.resources.electric_bike
import io.github.solcott.activitytracker.composeApp.resources.exercise
import io.github.solcott.activitytracker.composeApp.resources.explore
import io.github.solcott.activitytracker.composeApp.resources.fitness_center
import io.github.solcott.activitytracker.composeApp.resources.flight
import io.github.solcott.activitytracker.composeApp.resources.forest
import io.github.solcott.activitytracker.composeApp.resources.hiking
import io.github.solcott.activitytracker.composeApp.resources.hot_tub
import io.github.solcott.activitytracker.composeApp.resources.ice_skating
import io.github.solcott.activitytracker.composeApp.resources.kayaking
import io.github.solcott.activitytracker.composeApp.resources.kitesurfing
import io.github.solcott.activitytracker.composeApp.resources.local_fire_department
import io.github.solcott.activitytracker.composeApp.resources.map
import io.github.solcott.activitytracker.composeApp.resources.monitor_heart
import io.github.solcott.activitytracker.composeApp.resources.mountain_flag
import io.github.solcott.activitytracker.composeApp.resources.music_note
import io.github.solcott.activitytracker.composeApp.resources.nordic_walking
import io.github.solcott.activitytracker.composeApp.resources.paragliding
import io.github.solcott.activitytracker.composeApp.resources.pets
import io.github.solcott.activitytracker.composeApp.resources.phishing
import io.github.solcott.activitytracker.composeApp.resources.playground
import io.github.solcott.activitytracker.composeApp.resources.pool
import io.github.solcott.activitytracker.composeApp.resources.roller_skating
import io.github.solcott.activitytracker.composeApp.resources.rowing
import io.github.solcott.activitytracker.composeApp.resources.sailing
import io.github.solcott.activitytracker.composeApp.resources.sauna
import io.github.solcott.activitytracker.composeApp.resources.scooter
import io.github.solcott.activitytracker.composeApp.resources.scuba_diving
import io.github.solcott.activitytracker.composeApp.resources.self_improvement
import io.github.solcott.activitytracker.composeApp.resources.severe_cold
import io.github.solcott.activitytracker.composeApp.resources.skateboarding
import io.github.solcott.activitytracker.composeApp.resources.snowboarding
import io.github.solcott.activitytracker.composeApp.resources.snowmobile
import io.github.solcott.activitytracker.composeApp.resources.snowshoeing
import io.github.solcott.activitytracker.composeApp.resources.spa
import io.github.solcott.activitytracker.composeApp.resources.sports_and_outdoors
import io.github.solcott.activitytracker.composeApp.resources.sports_baseball
import io.github.solcott.activitytracker.composeApp.resources.sports_basketball
import io.github.solcott.activitytracker.composeApp.resources.sports_cricket
import io.github.solcott.activitytracker.composeApp.resources.sports_esports
import io.github.solcott.activitytracker.composeApp.resources.sports_football
import io.github.solcott.activitytracker.composeApp.resources.sports_golf
import io.github.solcott.activitytracker.composeApp.resources.sports_gymnastics
import io.github.solcott.activitytracker.composeApp.resources.sports_handball
import io.github.solcott.activitytracker.composeApp.resources.sports_hockey
import io.github.solcott.activitytracker.composeApp.resources.sports_kabaddi
import io.github.solcott.activitytracker.composeApp.resources.sports_martial_arts
import io.github.solcott.activitytracker.composeApp.resources.sports_mma
import io.github.solcott.activitytracker.composeApp.resources.sports_motorsports
import io.github.solcott.activitytracker.composeApp.resources.sports_rugby
import io.github.solcott.activitytracker.composeApp.resources.sports_score
import io.github.solcott.activitytracker.composeApp.resources.sports_soccer
import io.github.solcott.activitytracker.composeApp.resources.sports_tennis
import io.github.solcott.activitytracker.composeApp.resources.sports_volleyball
import io.github.solcott.activitytracker.composeApp.resources.sprint
import io.github.solcott.activitytracker.composeApp.resources.stairs
import io.github.solcott.activitytracker.composeApp.resources.stairs_2
import io.github.solcott.activitytracker.composeApp.resources.stroller
import io.github.solcott.activitytracker.composeApp.resources.surfing
import io.github.solcott.activitytracker.composeApp.resources.swords
import io.github.solcott.activitytracker.composeApp.resources.target
import io.github.solcott.activitytracker.composeApp.resources.trip_origin
import io.github.solcott.activitytracker.composeApp.resources.two_wheeler
import io.github.solcott.activitytracker.composeApp.resources.waves
import io.github.solcott.activitytracker.composeApp.resources.yard
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Each sport's icon. The drawables are Material Symbols (Outlined, 24px, Apache 2.0) from google/material-design-icons, vendored into
 * `composeResources/drawable` under their symbol names, with the `?attr` tint stripped and the fill made opaque so [Icon] can tint them.
 * Several sports share a symbol where Material has none of their own (every racket sport is a tennis racket).
 */
internal val SPORT_ICONS: Map<String, DrawableResource> =
    mapOf(
        Sport.RUN to Res.drawable.directions_run,
        Sport.RIDE to Res.drawable.directions_bike,
        Sport.HANDCYCLE to Res.drawable.accessible_forward,
        Sport.SWIM to Res.drawable.pool,
        Sport.MULTISPORT to Res.drawable.sports_score,
        Sport.WATER_FITNESS to Res.drawable.pool,
        Sport.WALK to Res.drawable.directions_walk,
        Sport.HIKE to Res.drawable.hiking,
        Sport.RUCKING to Res.drawable.backpack,
        Sport.MOUNTAINEERING to Res.drawable.mountain_flag,
        Sport.CLIMB to Res.drawable.altitude,
        Sport.INLINE_SKATE to Res.drawable.roller_skating,
        Sport.HORSEBACK to Res.drawable.explore,
        Sport.GOLF to Res.drawable.sports_golf,
        Sport.DISC_GOLF to Res.drawable.album,
        Sport.ARCHERY to Res.drawable.target,
        Sport.HUNT to Res.drawable.forest,
        Sport.FISH to Res.drawable.phishing,
        Sport.ADVENTURE_RACE to Res.drawable.explore,
        Sport.EXPEDITION to Res.drawable.camping,
        Sport.WHEELCHAIR to Res.drawable.accessible,
        Sport.SKATEBOARD to Res.drawable.skateboarding,
        Sport.KICK_SCOOTER to Res.drawable.scooter,
        Sport.ORIENTEERING to Res.drawable.map,
        Sport.PARKOUR to Res.drawable.sprint,
        Sport.SKI to Res.drawable.downhill_skiing,
        Sport.SNOWBOARD to Res.drawable.snowboarding,
        Sport.XC_SKI to Res.drawable.nordic_walking,
        Sport.SNOWSHOE to Res.drawable.snowshoeing,
        Sport.ICE_SKATE to Res.drawable.ice_skating,
        Sport.CURLING to Res.drawable.severe_cold,
        Sport.ROW to Res.drawable.rowing,
        Sport.KAYAK to Res.drawable.kayaking,
        Sport.CANOE to Res.drawable.kayaking,
        Sport.SUP to Res.drawable.surfing,
        Sport.SURF to Res.drawable.surfing,
        Sport.KITEBOARD to Res.drawable.kitesurfing,
        Sport.WINDSURF to Res.drawable.sailing,
        Sport.SAIL to Res.drawable.sailing,
        Sport.WAKEBOARD to Res.drawable.surfing,
        Sport.WAKESURF to Res.drawable.surfing,
        Sport.WATER_SKI to Res.drawable.waves,
        Sport.WHITEWATER to Res.drawable.kayaking,
        Sport.TUBE to Res.drawable.waves,
        Sport.SNORKEL to Res.drawable.scuba_diving,
        Sport.BOAT to Res.drawable.directions_boat,
        Sport.DIVE to Res.drawable.scuba_diving,
        Sport.STRENGTH to Res.drawable.fitness_center,
        Sport.CARDIO to Res.drawable.monitor_heart,
        Sport.HIIT to Res.drawable.local_fire_department,
        Sport.YOGA to Res.drawable.self_improvement,
        Sport.PILATES to Res.drawable.accessibility_new,
        Sport.MOBILITY to Res.drawable.accessibility_new,
        Sport.ELLIPTICAL to Res.drawable.exercise,
        Sport.STAIR_STEPPER to Res.drawable.stairs_2,
        Sport.FLOOR_CLIMB to Res.drawable.stairs,
        Sport.JUMP_ROPE to Res.drawable.exercise,
        Sport.BOXING to Res.drawable.sports_mma,
        Sport.MMA to Res.drawable.sports_martial_arts,
        Sport.WRESTLING to Res.drawable.sports_kabaddi,
        Sport.FENCING to Res.drawable.swords,
        Sport.TAI_CHI to Res.drawable.sports_martial_arts,
        Sport.DANCE to Res.drawable.music_note,
        Sport.GYMNASTICS to Res.drawable.sports_gymnastics,
        Sport.RECOVERY to Res.drawable.hot_tub,
        Sport.SOCCER to Res.drawable.sports_soccer,
        Sport.AMERICAN_FOOTBALL to Res.drawable.sports_football,
        Sport.BASEBALL to Res.drawable.sports_baseball,
        Sport.SOFTBALL to Res.drawable.sports_baseball,
        Sport.BASKETBALL to Res.drawable.sports_basketball,
        Sport.VOLLEYBALL to Res.drawable.sports_volleyball,
        Sport.ICE_HOCKEY to Res.drawable.sports_hockey,
        Sport.FIELD_HOCKEY to Res.drawable.sports_hockey,
        Sport.LACROSSE to Res.drawable.sports_hockey,
        Sport.RUGBY to Res.drawable.sports_rugby,
        Sport.CRICKET to Res.drawable.sports_cricket,
        Sport.ULTIMATE to Res.drawable.album,
        Sport.HANDBALL to Res.drawable.sports_handball,
        Sport.WATER_POLO to Res.drawable.pool,
        Sport.AUSTRALIAN_FOOTBALL to Res.drawable.sports_rugby,
        Sport.ROLLER_HOCKEY to Res.drawable.sports_hockey,
        Sport.FLOORBALL to Res.drawable.sports_hockey,
        Sport.TENNIS to Res.drawable.sports_tennis,
        Sport.PICKLEBALL to Res.drawable.sports_tennis,
        Sport.PADEL to Res.drawable.sports_tennis,
        Sport.BADMINTON to Res.drawable.badminton,
        Sport.SQUASH to Res.drawable.sports_tennis,
        Sport.RACQUETBALL to Res.drawable.sports_tennis,
        Sport.TABLE_TENNIS to Res.drawable.sports_tennis,
        Sport.PLATFORM_TENNIS to Res.drawable.sports_tennis,
        Sport.MOTORCYCLE to Res.drawable.two_wheeler,
        Sport.MOTOCROSS to Res.drawable.sports_motorsports,
        Sport.ATV to Res.drawable.sports_motorsports,
        Sport.SNOWMOBILE to Res.drawable.snowmobile,
        Sport.OVERLAND to Res.drawable.directions_car,
        Sport.MEDITATION to Res.drawable.spa,
        Sport.BREATHWORK to Res.drawable.air,
        Sport.FLYING to Res.drawable.flight,
        Sport.SKYDIVE to Res.drawable.paragliding,
        Sport.GAMING to Res.drawable.sports_esports,
        Sport.BOWLING to Res.drawable.trip_origin,
        Sport.PLAY to Res.drawable.playground,
        Sport.YARD_WORK to Res.drawable.yard,
        Sport.HOUSEWORK to Res.drawable.cleaning_services,
        Sport.OTHER to Res.drawable.sports_and_outdoors,
    )

/** Sub-sports that look different enough from their sport to get their own icon. */
private val SUB_SPORT_ICONS: Map<ActivityType, DrawableResource> =
    mapOf(
        ActivityType(Sport.RIDE, SubSport.E_BIKE) to Res.drawable.electric_bike,
        ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN) to Res.drawable.electric_bike,
        ActivityType(Sport.SWIM, SubSport.OPEN_WATER) to Res.drawable.waves,
        ActivityType(Sport.WALK, SubSport.NORDIC) to Res.drawable.nordic_walking,
        ActivityType(Sport.WHEELCHAIR, SubSport.RUN_PACE) to Res.drawable.accessible_forward,
        ActivityType(Sport.FLYING, SubSport.PARAGLIDING) to Res.drawable.paragliding,
        ActivityType(Sport.WALK, SubSport.DOG) to Res.drawable.pets,
        ActivityType(Sport.WALK, SubSport.STROLLER) to Res.drawable.stroller,
        ActivityType(Sport.RUN, SubSport.STROLLER) to Res.drawable.stroller,
        ActivityType(Sport.RECOVERY, SubSport.SAUNA) to Res.drawable.sauna,
        ActivityType(Sport.RECOVERY, SubSport.COLD_PLUNGE) to Res.drawable.severe_cold,
    )

/** The icon for [type]: its sub-sport's, else its sport's, else a generic one for a sport another client added. */
fun activityTypeIcon(type: ActivityType): DrawableResource =
    SUB_SPORT_ICONS[type] ?: SPORT_ICONS[type.sport] ?: Res.drawable.sports_and_outdoors

/** [type]'s icon. Decorative by default, since it always sits next to the type's label. */
@Composable
fun ActivityTypeIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    Icon(vectorResource(activityTypeIcon(type)), contentDescription, modifier, tint)
}
