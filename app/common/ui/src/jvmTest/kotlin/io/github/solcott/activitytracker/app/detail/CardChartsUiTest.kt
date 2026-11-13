package io.github.solcott.activitytracker.app.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared card draws the charts the native card draws (dev/shared-display-text): [CardLines]' charts, headers and descriptions. */
@OptIn(ExperimentalTestApi::class)
class CardChartsUiTest {
    @Test
    fun eachChartIsCardLinesWithItsHeader() = runComposeUiTest {
        val card = ActivityDetailSamples.rideElevation
        val charts = ActivityDetailSamples.rideCharts
        val lines = CardLines.of(card, charts, UnitSystem.Metric)
        var descriptions = emptyList<String>()
        var titles = emptyList<String>()
        setContent {
            AppPreview {
                descriptions = lines.map { it.chart.description.string() }
                titles = lines.mapNotNull { it.title?.string() }
                CardCharts(card, charts, missingZones = false, onSetThresholds = null)
            }
        }
        // Elevation and the grade under it: the grade titled, as the card's own title names only elevation.
        assertEquals(2, descriptions.size)
        assertEquals(listOf("Grade"), titles)
        descriptions.forEach { onNodeWithContentDescription(it).assertExists() }
        onNodeWithText("Grade").assertExists()
    }

    @Test
    fun aPaceLineReadsItsPaceDescription() = runComposeUiTest {
        val card = ActivityDetailSamples.runPace
        val charts = ActivityDetailSamples.rideCharts
        var description = ""
        setContent {
            AppPreview {
                description = CardLines.of(card, charts, UnitSystem.Metric).first().chart.description.string()
                CardCharts(card, charts, missingZones = false, onSetThresholds = null)
            }
        }
        onNodeWithContentDescription(description).assertExists()
    }
}
