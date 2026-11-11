package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a segment is, as text (segments/segment-lifts, dev/shared-display-text). */
class SegmentTextsTest {
    @Test
    fun aSportGroupIsNamedByItsToken() {
        assertEquals(
            AppText.Named("segment_sport_ride", fallback = AppText.written("ride")),
            SegmentTexts.sportGroup(SegmentSportGroup.RIDE),
        )
        assertEquals(AppText.written("kite"), SegmentTexts.sportGroup("kite").let { (it as AppText.Named).fallback })
    }

    @Test
    fun aSegmentsTypeIsALiftARunOrItsGroup() {
        assertEquals(AppText.named("segment_kind_ski_lift"), SegmentTexts.type(SegmentSportGroup.ALPINE, SegmentKind.LIFT))
        assertEquals(AppText.named("segment_kind_ski_run"), SegmentTexts.type(SegmentSportGroup.ALPINE, null))
        assertEquals(AppText.named("segment_kind_ski_run"), SegmentTexts.type(SegmentSportGroup.ALPINE, SegmentKind.RUN))
        assertEquals(SegmentTexts.sportGroup(SegmentSportGroup.HIKE), SegmentTexts.type(SegmentSportGroup.HIKE, null))
    }
}
