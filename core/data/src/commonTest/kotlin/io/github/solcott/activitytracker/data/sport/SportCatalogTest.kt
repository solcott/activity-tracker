package io.github.solcott.activitytracker.data.sport

import io.github.solcott.activitytracker.activityfile.DecodedActivityMapper
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportCatalogTest {
    @Test
    fun sportsAreUniqueAndSubSportsUniquePerSport() {
        val sports = SportCatalog.sports.map { it.sport }
        assertEquals(sports.size, sports.toSet().size)
        SportCatalog.sports.forEach { info ->
            val subs = info.subSports.map { it.subSport }
            assertEquals(subs.size, subs.toSet().size, info.sport)
        }
    }

    @Test
    fun everyCategoryTypeIsKnownAndListedOnce() {
        SportCatalog.categories.forEach { (category, types) ->
            assertTrue(types.isNotEmpty(), "$category is empty")
            types.forEach { assertTrue(SportCatalog.isKnown(it), "$category lists unknown $it") }
        }
        val known =
            SportCatalog.sports.flatMap { info ->
                listOf(ActivityType(info.sport)) + info.subSports.map { ActivityType(info.sport, it.subSport) }
            }
        known.forEach { type ->
            val listing = SportCatalog.categories.filter { type in it.types }.map { it.category }
            assertEquals(1, SportCatalog.categories.sumOf { it.types.count(type::equals) }, "$type is listed in $listing")
        }
    }

    @Test
    fun subSportsFiledUnderAnotherCategory() {
        assertEquals(SportCategory.Gym, categoryOf(ActivityType(Sport.ROW, SubSport.INDOOR)))
        assertEquals(SportCategory.WaterSports, categoryOf(ActivityType(Sport.ROW)))
        assertEquals(SportCategory.Running, categoryOf(ActivityType(Sport.RUN, SubSport.TREADMILL)))
        assertEquals(SportCategory.Cycling, categoryOf(ActivityType(Sport.RIDE, SubSport.INDOOR)))
        assertEquals(SportCategory.Walking, categoryOf(ActivityType(Sport.WALK)))
    }

    @Test
    fun indoorFollowsSportOrSubSport() {
        assertTrue(SportCatalog.isIndoor(ActivityType(Sport.RIDE, SubSport.INDOOR)))
        assertFalse(SportCatalog.isIndoor(ActivityType(Sport.RIDE, SubSport.MOUNTAIN)))
        assertTrue(SportCatalog.isIndoor(ActivityType(Sport.STRENGTH)))
        assertFalse(SportCatalog.isIndoor(ActivityType("someFutureSport")))
        assertFalse(SportCatalog.isKnown(ActivityType(Sport.RUN, SubSport.MOUNTAIN)), "a sub-sport of another sport")
    }

    @Test
    fun unknownSportsReadAsPace() {
        assertEquals(RateKind.Speed, SportCatalog.rate(Sport.RIDE))
        assertEquals(RateKind.SwimPace, SportCatalog.rate(Sport.SWIM))
        assertEquals(RateKind.None, SportCatalog.rate(Sport.YOGA))
        assertEquals(RateKind.Pace, SportCatalog.rate("someFutureSport"))
    }

    @Test
    fun coversEveryGarminActivityProfile() {
        assertEquals(132, GARMIN_PROFILES.size, "every profile heading except the \"Start/Stop Activity Profile Only\" footnote")
        GARMIN_PROFILES.forEach { (profile, type) ->
            assertTrue(SportCatalog.isKnown(type), "Garmin's $profile → $type isn't in the catalog")
        }
    }

    @Test
    fun coversEveryStravaSportTypeAndImportsIt() {
        assertEquals(55, STRAVA_SPORT_TYPES.size)
        STRAVA_SPORT_TYPES.forEach { (name, type) ->
            assertTrue(SportCatalog.isKnown(type), "Strava's $name → $type isn't in the catalog")
            assertEquals(type, DecodedActivityMapper.activityType(name), "a GPX of Strava type $name")
        }
    }

    @Test fun coversEveryAppleWorkoutType() = assertCovers("Apple", APPLE_WORKOUT_TYPES, expected = 84)

    @Test fun coversEveryHealthConnectExerciseType() = assertCovers("Health Connect", HEALTH_CONNECT_EXERCISE_TYPES, expected = 61)

    private fun categoryOf(type: ActivityType): SportCategory = SportCatalog.categories.single { type in it.types }.category

    private fun assertCovers(provider: String, types: Map<String, ActivityType>, expected: Int) {
        assertEquals(expected, types.size, "$provider's type count")
        types.forEach { (name, type) ->
            assertTrue(SportCatalog.isKnown(type), "$provider's $name → $type isn't in the catalog")
            val generic = name.equals("other", ignoreCase = true) || name == "OTHER_WORKOUT"
            if (type.sport == Sport.OTHER) assertTrue(generic || name in NOT_YET_MODELED, "$provider's $name maps to other")
        }
    }

    private companion object {
        /**
         * Every activity profile in Garmin's "Activity Profile Options on Garmin Wearables" FAQ, spelled as Garmin does, and the type it
         * becomes. Profiles that are device tools rather than activities (Track Me, Tactical, golf swing drills) map to the closest sport.
         */
        val GARMIN_PROFILES =
            mapOf(
                "Adventure Race" to ActivityType(Sport.ADVENTURE_RACE),
                "American Football" to ActivityType(Sport.AMERICAN_FOOTBALL),
                "Apnea Dive" to ActivityType(Sport.DIVE, SubSport.APNEA),
                "Apnea Hunt" to ActivityType(Sport.DIVE, SubSport.APNEA_HUNT),
                "Archery" to ActivityType(Sport.ARCHERY),
                "ArcheryX" to ActivityType(Sport.ARCHERY),
                "ATV" to ActivityType(Sport.ATV),
                "Backcountry Ski" to ActivityType(Sport.SKI, SubSport.BACKCOUNTRY),
                "Backcountry Snowboard" to ActivityType(Sport.SNOWBOARD, SubSport.BACKCOUNTRY),
                "Badminton" to ActivityType(Sport.BADMINTON),
                "Baseball" to ActivityType(Sport.BASEBALL),
                "Basketball" to ActivityType(Sport.BASKETBALL),
                "Bike" to ActivityType(Sport.RIDE),
                "Bike Commute" to ActivityType(Sport.RIDE, SubSport.COMMUTE),
                "Bike Indoor" to ActivityType(Sport.RIDE, SubSport.INDOOR),
                "Bike Tour" to ActivityType(Sport.RIDE, SubSport.TOUR),
                "BMX" to ActivityType(Sport.RIDE, SubSport.BMX),
                "Boat" to ActivityType(Sport.BOAT),
                "Bouldering" to ActivityType(Sport.CLIMB, SubSport.BOULDERING),
                "Boxing" to ActivityType(Sport.BOXING),
                "Breathwork" to ActivityType(Sport.BREATHWORK),
                "Cardio" to ActivityType(Sport.CARDIO),
                "CCR" to ActivityType(Sport.DIVE, SubSport.CCR),
                "Climb" to ActivityType(Sport.CLIMB),
                "Climb Indoor" to ActivityType(Sport.CLIMB, SubSport.INDOOR),
                "Cricket" to ActivityType(Sport.CRICKET),
                "Cyclocross" to ActivityType(Sport.RIDE, SubSport.CYCLOCROSS),
                "Dance Fitness" to ActivityType(Sport.DANCE, SubSport.CARDIO),
                "Disc Golf" to ActivityType(Sport.DISC_GOLF),
                "eBike" to ActivityType(Sport.RIDE, SubSport.E_BIKE),
                "Elliptical" to ActivityType(Sport.ELLIPTICAL),
                "eMTB" to ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN),
                "Expedition" to ActivityType(Sport.EXPEDITION),
                "Field Hockey" to ActivityType(Sport.FIELD_HOCKEY),
                "Fish" to ActivityType(Sport.FISH),
                "Floor Climb" to ActivityType(Sport.FLOOR_CLIMB),
                "Flying" to ActivityType(Sport.FLYING),
                "Gaming" to ActivityType(Sport.GAMING),
                "Gauge" to ActivityType(Sport.DIVE, SubSport.GAUGE),
                "Golf" to ActivityType(Sport.GOLF),
                "Gravel Bike" to ActivityType(Sport.RIDE, SubSport.GRAVEL),
                "Gymnastics" to ActivityType(Sport.GYMNASTICS),
                "Handcycle" to ActivityType(Sport.HANDCYCLE),
                "Handcycle Indoor" to ActivityType(Sport.HANDCYCLE, SubSport.INDOOR),
                "HIIT" to ActivityType(Sport.HIIT),
                "Hike" to ActivityType(Sport.HIKE),
                "Horseback" to ActivityType(Sport.HORSEBACK),
                "Hunt" to ActivityType(Sport.HUNT),
                "Ice Hockey" to ActivityType(Sport.ICE_HOCKEY),
                "Ice Skating" to ActivityType(Sport.ICE_SKATE),
                "Indoor Track" to ActivityType(Sport.RUN, SubSport.INDOOR_TRACK),
                "Inline Skating" to ActivityType(Sport.INLINE_SKATE),
                "Jump Rope" to ActivityType(Sport.JUMP_ROPE),
                "Jumpmaster" to ActivityType(Sport.SKYDIVE),
                "Kayak" to ActivityType(Sport.KAYAK),
                "Kiteboard" to ActivityType(Sport.KITEBOARD),
                "Lacrosse" to ActivityType(Sport.LACROSSE),
                "Meditation" to ActivityType(Sport.MEDITATION),
                "Mixed Martial Arts" to ActivityType(Sport.MMA),
                "Mixed Session" to ActivityType(Sport.MULTISPORT, SubSport.MIXED),
                "Mobility" to ActivityType(Sport.MOBILITY),
                "Motocross" to ActivityType(Sport.MOTOCROSS),
                "Motorcycle" to ActivityType(Sport.MOTORCYCLE),
                "Mountaineering" to ActivityType(Sport.MOUNTAINEERING),
                "MTB" to ActivityType(Sport.RIDE, SubSport.MOUNTAIN),
                "Multi-Gas" to ActivityType(Sport.DIVE, SubSport.MULTI_GAS),
                "Obstacle Racing" to ActivityType(Sport.RUN, SubSport.OBSTACLE),
                "Open Water" to ActivityType(Sport.SWIM, SubSport.OPEN_WATER),
                "Other" to ActivityType(Sport.OTHER),
                "Overland" to ActivityType(Sport.OVERLAND),
                "Padel" to ActivityType(Sport.PADEL),
                "Pickelball" to ActivityType(Sport.PICKLEBALL),
                "Pilates" to ActivityType(Sport.PILATES),
                "Platform Tennis" to ActivityType(Sport.PLATFORM_TENNIS),
                "Pool Apnea" to ActivityType(Sport.DIVE, SubSport.POOL_APNEA),
                "Pool Swim" to ActivityType(Sport.SWIM, SubSport.POOL),
                "Raquetball" to ActivityType(Sport.RACQUETBALL),
                "Road Bike" to ActivityType(Sport.RIDE, SubSport.ROAD),
                "Row" to ActivityType(Sport.ROW),
                "Row Indoor" to ActivityType(Sport.ROW, SubSport.INDOOR),
                "Rucking" to ActivityType(Sport.RUCKING),
                "Rugby" to ActivityType(Sport.RUGBY),
                "Run" to ActivityType(Sport.RUN),
                "Run Indoor" to ActivityType(Sport.RUN, SubSport.INDOOR_TRACK),
                "Sail" to ActivityType(Sport.SAIL),
                "Sail Expedition" to ActivityType(Sport.SAIL, SubSport.EXPEDITION),
                "Sail Race" to ActivityType(Sport.SAIL, SubSport.RACE),
                "Scuba Dive" to ActivityType(Sport.DIVE),
                "Single-Gas" to ActivityType(Sport.DIVE, SubSport.SINGLE_GAS),
                "Ski" to ActivityType(Sport.SKI),
                "Snorkel" to ActivityType(Sport.SNORKEL),
                "Snowboard" to ActivityType(Sport.SNOWBOARD),
                "Snowmobile" to ActivityType(Sport.SNOWMOBILE),
                "Snowshoe" to ActivityType(Sport.SNOWSHOE),
                "Soccer/Football" to ActivityType(Sport.SOCCER),
                "Softball" to ActivityType(Sport.SOFTBALL),
                "Squash" to ActivityType(Sport.SQUASH),
                "Stair Stepper" to ActivityType(Sport.STAIR_STEPPER),
                "Strength" to ActivityType(Sport.STRENGTH),
                "SUP (Stand Up Paddle Boarding)" to ActivityType(Sport.SUP),
                "Surf" to ActivityType(Sport.SURF),
                "Swim" to ActivityType(Sport.SWIM),
                "Swing Tempo" to ActivityType(Sport.GOLF),
                "Table Tennis" to ActivityType(Sport.TABLE_TENNIS),
                "Tactical" to ActivityType(Sport.OTHER),
                "Tempo Training" to ActivityType(Sport.GOLF),
                "Tennis" to ActivityType(Sport.TENNIS),
                "Track Me" to ActivityType(Sport.OTHER),
                "Track Run" to ActivityType(Sport.RUN, SubSport.TRACK),
                "Trail Run" to ActivityType(Sport.RUN, SubSport.TRAIL),
                "Train" to ActivityType(Sport.CARDIO),
                "Treadmill" to ActivityType(Sport.RUN, SubSport.TREADMILL),
                "Treadmill Walk" to ActivityType(Sport.WALK, SubSport.TREADMILL),
                "Triathlon/Multisport" to ActivityType(Sport.MULTISPORT, SubSport.TRIATHLON),
                "TruSwing" to ActivityType(Sport.GOLF),
                "Tube" to ActivityType(Sport.TUBE),
                "Ultimate Disc" to ActivityType(Sport.ULTIMATE),
                "Ultra Run" to ActivityType(Sport.RUN, SubSport.ULTRA),
                "Virtual Run" to ActivityType(Sport.RUN, SubSport.VIRTUAL),
                "Volleyball" to ActivityType(Sport.VOLLEYBALL),
                "Wakeboard" to ActivityType(Sport.WAKEBOARD),
                "Wakesurf" to ActivityType(Sport.WAKESURF),
                "Walk" to ActivityType(Sport.WALK),
                "Walk Indoor" to ActivityType(Sport.WALK, SubSport.INDOOR),
                "Water Ski" to ActivityType(Sport.WATER_SKI),
                "Whitewater" to ActivityType(Sport.WHITEWATER),
                "Windsurf" to ActivityType(Sport.WINDSURF),
                "XC Classic Ski" to ActivityType(Sport.XC_SKI, SubSport.CLASSIC),
                "XC Skate Ski" to ActivityType(Sport.XC_SKI, SubSport.SKATE),
                "XC Ski" to ActivityType(Sport.XC_SKI),
                "XC Ski Indoor" to ActivityType(Sport.XC_SKI, SubSport.INDOOR),
                "Yoga" to ActivityType(Sport.YOGA),
            )
    }
}
