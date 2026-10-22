package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentBoardTest {
    @Test
    fun anActivitysBoardIsItsSport() {
        assertEquals(Sport.RIDE, SegmentBoard.of(Sport.RIDE, SubSport.ROAD))
        assertEquals(Sport.HANDCYCLE, SegmentBoard.of(Sport.HANDCYCLE, null))
        assertEquals(Sport.SKI, SegmentBoard.of(Sport.SKI, SubSport.BACKCOUNTRY))
        assertEquals(Sport.WALK, SegmentBoard.of(Sport.WALK, null))
    }

    @Test
    fun eBikeRidesHaveTheirOwnBoard() {
        assertEquals(SegmentBoard.E_BIKE, SegmentBoard.of(Sport.RIDE, SubSport.E_BIKE))
        assertEquals(SegmentBoard.E_BIKE, SegmentBoard.of(Sport.RIDE, SubSport.E_MOUNTAIN))
    }

    @Test
    fun activitiesThatCantMatchHaveNoBoard() {
        assertEquals(null, SegmentBoard.of(Sport.RIDE, SubSport.INDOOR))
        assertEquals(null, SegmentBoard.of(Sport.SKI, null))
    }

    @Test
    fun everyGroupedSportIsABoard() {
        val sports =
            Sport::class
                .java
                .declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .map { it.get(null) as String }
        val grouped = sports.filter { SegmentSport.of(it, null) != null || SegmentSport.of(it, SubSport.BACKCOUNTRY) != null }
        assertEquals(grouped.toSet() + SegmentBoard.E_BIKE, SegmentBoard.all.toSet())
    }
}
