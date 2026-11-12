package io.github.solcott.activitytracker.app.chart

import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineChartTest {
    private fun chart(points: List<LinePoint>) =
        LineChart.of(
            points = points,
            xMin = points.first().x,
            xMax = points.last().x,
            xTicks = listOf(ChartTick(0.0, AppText.written("0"))),
            yLabel = { AppText.written("${it.toInt()} m") },
            readouts = points.map { AppText.written("at ${it.x}") },
            description = AppText.written("a line"),
        )

    @Test
    fun theNearestPointIsTheEarlierOnATie() {
        val line = chart(listOf(LinePoint(0.0, 1.0), LinePoint(1.0, 2.0), LinePoint(2.0, 3.0)))
        assertEquals(0, line.nearest(-5.0))
        assertEquals(0, line.nearest(0.5))
        assertEquals(1, line.nearest(0.6))
        assertEquals(2, line.nearest(9.0))
        assertEquals(AppText.written("at 1.0"), line.readout(1.2))
    }

    @Test
    fun theAxisIsTheSharedRoundOneWithALabelPerGridline() {
        val line = chart(listOf(LinePoint(0.0, 1_410.0), LinePoint(1.0, 1_490.0)))
        assertEquals(listOf(1_400.0, 1_450.0, 1_500.0), line.yTicks.map { it.value })
        assertEquals(AppText.written("1450 m"), line.labelAt(1_450.0, line.yTicks))
        assertNull(line.labelAt(1_425.0, line.yTicks))
    }
}
