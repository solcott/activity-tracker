package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrubGestureTest {
    private fun shown(vararg events: ScrubEvent): List<Boolean> {
        val gesture = ScrubGesture(slop = 10f)
        return events.map(gesture::shows)
    }

    @Test
    fun aSidewaysDragShowsAndKeepsTheReadout() {
        assertEquals(
            listOf(true, true, true, true),
            shown(
                ScrubEvent.Press(Offset(10f, 50f)),
                ScrubEvent.Move(Offset(40f, 55f)),
                ScrubEvent.Move(Offset(80f, 70f)),
                ScrubEvent.Release,
            ),
        )
    }

    @Test
    fun aMostlyVerticalDragHidesItForTheRestOfTheGesture() {
        assertEquals(
            listOf(true, true, false, false, false),
            shown(
                ScrubEvent.Press(Offset(10f, 50f)),
                ScrubEvent.Move(Offset(12f, 58f)),
                ScrubEvent.Move(Offset(14f, 70f)),
                ScrubEvent.Move(Offset(60f, 72f)),
                ScrubEvent.Release,
            ),
        )
    }

    @Test
    fun theNextPressShowsItAgainAfterAScroll() {
        val gesture = ScrubGesture(slop = 10f)
        listOf(ScrubEvent.Press(Offset(0f, 0f)), ScrubEvent.Move(Offset(0f, 30f)), ScrubEvent.Release).forEach(gesture::shows)
        assertEquals(true, gesture.shows(ScrubEvent.Press(Offset(20f, 20f))))
    }

    @Test
    fun aTapKeepsTheReadout() {
        assertEquals(listOf(true, true), shown(ScrubEvent.Press(Offset(10f, 10f)), ScrubEvent.Release))
    }

    @Test
    fun hoveringShowsItAndLeavingHidesIt() {
        assertEquals(
            listOf(true, true, true, false),
            shown(
                ScrubEvent.Enter,
                ScrubEvent.Move(Offset(10f, 10f)),
                ScrubEvent.Move(Offset(10f, 90f)),
                ScrubEvent.Exit(inBounds = false),
            ),
        )
    }

    @Test
    fun anExitInsideTheChartKeepsIt() {
        assertEquals(
            listOf(true, true, true),
            shown(ScrubEvent.Press(Offset(10f, 10f)), ScrubEvent.Release, ScrubEvent.Exit(inBounds = true)),
        )
    }
}
