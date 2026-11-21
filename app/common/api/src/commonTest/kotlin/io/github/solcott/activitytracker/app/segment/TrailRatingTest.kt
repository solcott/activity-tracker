package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.segment.TrailRating.DIFFICULT
import io.github.solcott.activitytracker.app.segment.TrailRating.EASIEST
import io.github.solcott.activitytracker.app.segment.TrailRating.EASY
import io.github.solcott.activitytracker.app.segment.TrailRating.EXTREME
import io.github.solcott.activitytracker.app.segment.TrailRating.INTERMEDIATE
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.ALPINE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.PADDLE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.SKI
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A trail's rating level for a sport group (segments/trail-ratings). */
class TrailRatingTest {
    @Test
    fun imbaBeatsTheMtbScaleOnARide() {
        assertEquals(DIFFICULT, TrailRating.of(TrailAttributes(mtbScaleImba = 3, mtbScale = 2), RIDE))
    }

    @Test
    fun mtbScaleAlone() {
        assertEquals(EXTREME, TrailRating.of(TrailAttributes(mtbScale = 5), RIDE))
    }

    @Test
    fun aRunOnARatedRideTrailIsUnrated() {
        assertNull(TrailRating.of(TrailAttributes(mtbScaleImba = 2), RUN))
    }

    @Test
    fun piste() {
        assertEquals(DIFFICULT, TrailRating.of(TrailAttributes(pisteDifficulty = "advanced"), ALPINE))
        assertEquals(EXTREME, TrailRating.of(TrailAttributes(pisteDifficulty = "freeride"), ALPINE))
    }

    @Test
    fun noTrailIsUnrated() {
        assertNull(TrailRating.of(null, RIDE))
        assertNull(TrailRating.of(TrailAttributes(), null))
    }

    @Test
    fun otherSportGroupsAreUnrated() {
        val all = TrailAttributes(mtbScaleImba = 1, sacScale = TrailSacScale.HIKING, pisteDifficulty = "easy")
        assertNull(TrailRating.of(all, SKI))
        assertNull(TrailRating.of(all, PADDLE))
    }

    @Test
    fun everyImbaValue() {
        assertEquals(
            listOf(EASIEST, EASY, INTERMEDIATE, DIFFICULT, EXTREME, null),
            (0..5).map { TrailRating.of(TrailAttributes(mtbScaleImba = it), RIDE) },
        )
    }

    @Test
    fun everyMtbScaleValue() {
        assertEquals(
            listOf(EASIEST, EASY, INTERMEDIATE, DIFFICULT, EXTREME, EXTREME, EXTREME, null),
            (0..7).map { TrailRating.of(TrailAttributes(mtbScale = it), RIDE) },
        )
        assertNull(TrailRating.of(TrailAttributes(mtbScale = -1), RIDE))
    }

    @Test
    fun everySacValue() {
        assertEquals(
            listOf(EASY, INTERMEDIATE, DIFFICULT, EXTREME, EXTREME, EXTREME),
            TrailSacScale.all.map { TrailRating.of(TrailAttributes(sacScale = it), HIKE) },
        )
        assertNull(TrailRating.of(TrailAttributes(sacScale = "unknown"), RUN))
    }

    @Test
    fun everyPisteValue() {
        assertEquals(
            listOf(EASIEST, EASY, INTERMEDIATE, DIFFICULT, EXTREME, EXTREME, EXTREME),
            TrailPisteDifficulty.all.map { TrailRating.of(TrailAttributes(pisteDifficulty = it), ALPINE) },
        )
    }

    @Test
    fun noSportTakesTheFirstScaleItHas() {
        val trail = TrailAttributes(mtbScale = 1, sacScale = TrailSacScale.DEMANDING_MOUNTAIN_HIKING)
        assertEquals(EASY, TrailRating.of(trail, null))
        assertEquals(DIFFICULT, TrailRating.of(trail, HIKE))
        assertEquals(EASIEST, TrailRating.of(TrailAttributes(sacScale = "x", pisteDifficulty = "novice"), null))
    }

    @Test
    fun labels() {
        assertEquals(AppText.named("trail_rating_intermediate"), INTERMEDIATE.label)
        assertEquals(AppText.named("trail_rating_unrated"), TrailRating.unratedLabel)
    }
}
