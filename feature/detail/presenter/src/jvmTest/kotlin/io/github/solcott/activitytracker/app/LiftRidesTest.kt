package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.LiftRides
import io.github.solcott.activitytracker.app.detail.isLiftRide
import io.github.solcott.activitytracker.app.detail.liftRidesOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals

/** A ski day's lifts apart from its runs, one entry per lift (segments/segment-lifts). */
class LiftRidesTest {
    private val activity = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kact")

    private fun uri(rkey: String) = AtUri("at://did:plc:importer/${Nsid.FEED_SEGMENT}/$rkey")

    private fun effort(rkey: String, name: String, start: Long, elapsed: Long, kind: String? = SegmentKind.LIFT) =
        EffortView(SegmentViewBasic(uri(rkey), name, "alpine", 1000, kind = kind), activity, start, elapsed, "2026-12-20")

    @Test
    fun eachLiftOnceInTheOrderFirstRiddenWithItsRidesAndTime() {
        val efforts =
            listOf(
                effort("quad", "Summit Quad", start = 900, elapsed = 400),
                effort("lynx", "Lynx", start = 1300, elapsed = 120, kind = null),
                effort("gondola", "Base Gondola", start = 0, elapsed = 540),
                effort("quad", "Summit Quad", start = 1500, elapsed = 410),
            )
        assertEquals(
            listOf(LiftRides(uri("gondola"), "Base Gondola", 1, 540), LiftRides(uri("quad"), "Summit Quad", 2, 810)),
            liftRidesOf(efforts),
        )
        assertEquals(listOf("Lynx"), efforts.filterNot(::isLiftRide).map { it.segment.name }, "runs stay with the segments")
    }

    @Test
    fun aDayWithoutLiftsHasNone() {
        assertEquals(emptyList(), liftRidesOf(listOf(effort("lynx", "Lynx", 0, 120, kind = SegmentKind.RUN))))
    }
}
