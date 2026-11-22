package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.chart.ChartSpan
import io.github.solcott.activitytracker.app.chart.LineChartSamples
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A shared chart tells its page where its readout is (segments/path-profile), and draws its spans without changing that. */
@OptIn(ExperimentalTestApi::class)
class LineChartScrubUiTest {
    private val chart =
        LineChartSamples.plain.withSpans(
            listOf(ChartSpan(0.0, 600.0, TrailRating.EASY), ChartSpan(900.0, 1_500.0, TrailRating.DIFFICULT, selected = true)),
        )

    @Test
    fun aTapReportsThePointItMarksAndLeavingReportsNull() = runComposeUiTest {
        val told = mutableListOf<Double?>()
        setContent { AppPreview { LineChartUi(chart, onScrub = { told += it }) } }

        onNodeWithContentDescription("Heart rate").performTouchInput {
            down(center)
            up()
        }
        waitForIdle()
        val marked = assertNotNull(told.lastOrNull(), "a tap marks a point")
        assertTrue(chart.points.any { it.x == marked }, "one of the chart's points")

        told.clear()
        onNodeWithContentDescription("Heart rate").performMouseInput {
            moveTo(centerLeft)
            moveTo(center)
            exit(Offset(-10f, centerY))
        }
        waitForIdle()
        assertNull(told.last(), "the pointer left, so the readout went")
    }

    @Test
    fun withoutAListenerNothingIsReported() = runComposeUiTest {
        setContent { AppPreview { LineChartUi(chart) } }
        onNodeWithContentDescription("Heart rate").performTouchInput {
            down(center)
            up()
        }
        waitForIdle()
        assertEquals(2, chart.spanCount)
    }
}
