package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentSport.Grouped
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins every known [Sport] to its segment group, so a new sport gets a deliberate choice. */
class SegmentSportTest {
    private val grouped =
        mapOf(
            Sport.RIDE to SegmentSportGroup.RIDE,
            Sport.HANDCYCLE to SegmentSportGroup.RIDE,
            Sport.RUN to SegmentSportGroup.RUN,
            Sport.HIKE to SegmentSportGroup.HIKE,
            Sport.WALK to SegmentSportGroup.HIKE,
            Sport.RUCKING to SegmentSportGroup.HIKE,
            Sport.SNOWSHOE to SegmentSportGroup.HIKE,
            Sport.XC_SKI to SegmentSportGroup.SKI,
            Sport.KAYAK to SegmentSportGroup.PADDLE,
            Sport.CANOE to SegmentSportGroup.PADDLE,
            Sport.SUP to SegmentSportGroup.PADDLE,
            Sport.ROW to SegmentSportGroup.PADDLE,
        )

    private fun constants(type: Class<*>): List<String> =
        type.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }.map { it.get(null) as String }

    @Test
    fun everyKnownSportIsPinned() {
        val sports = constants(Sport::class.java)
        check(sports.size > 50) { "reflection found ${sports.size} sports" }
        for (sport in sports) {
            val expected = grouped[sport]?.let { Grouped(it) }
            assertEquals(expected, SegmentSport.of(sport, null), sport)
        }
    }

    @Test
    fun indoorAndVirtualHaveNoGroup() {
        for (sub in listOf(SubSport.INDOOR, SubSport.VIRTUAL, SubSport.TREADMILL, SubSport.INDOOR_TRACK)) {
            for (sport in grouped.keys) assertEquals(null, SegmentSport.of(sport, sub), "$sport:$sub")
        }
    }

    @Test
    fun outdoorSubSportsKeepTheirGroup() {
        assertEquals(Grouped(SegmentSportGroup.RUN), SegmentSport.of(Sport.RUN, SubSport.TRAIL))
        assertEquals(Grouped(SegmentSportGroup.RUN), SegmentSport.of(Sport.RUN, SubSport.TRACK))
        assertEquals(Grouped(SegmentSportGroup.RIDE), SegmentSport.of(Sport.RIDE, SubSport.MOUNTAIN))
        assertEquals(Grouped(SegmentSportGroup.RIDE), SegmentSport.of(Sport.RIDE, SubSport.GRAVEL))
        assertEquals(Grouped(SegmentSportGroup.SKI), SegmentSport.of(Sport.XC_SKI, SubSport.SKATE))
        assertEquals(Grouped(SegmentSportGroup.HIKE), SegmentSport.of(Sport.WALK, SubSport.NORDIC))
    }

    @Test
    fun eBikesAreRidesMarkedEBike() {
        assertEquals(Grouped(SegmentSportGroup.RIDE, eBike = true), SegmentSport.of(Sport.RIDE, SubSport.E_BIKE))
        assertEquals(Grouped(SegmentSportGroup.RIDE, eBike = true), SegmentSport.of(Sport.RIDE, SubSport.E_MOUNTAIN))
    }

    @Test
    fun onlyBackcountrySkiingHasAGroup() {
        assertEquals(Grouped(SegmentSportGroup.SKI), SegmentSport.of(Sport.SKI, SubSport.BACKCOUNTRY))
        assertEquals(null, SegmentSport.of(Sport.SKI, null))
        assertEquals(null, SegmentSport.of(Sport.SKI, SubSport.RACE))
        assertEquals(null, SegmentSport.of(Sport.WHITEWATER, null))
    }

    @Test
    fun everyGroupHasAMinimumLength() {
        val minimums = SegmentSportGroup.all.associateWith { SegmentSportGroup.minMeters(it) }
        assertEquals(
            mapOf(
                SegmentSportGroup.RIDE to 500,
                SegmentSportGroup.RUN to 200,
                SegmentSportGroup.HIKE to 200,
                SegmentSportGroup.SKI to 200,
                SegmentSportGroup.PADDLE to 200,
            ),
            minimums,
        )
    }
}
