package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import kotlin.math.sin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private val heartRate = (0..60).map { SeriesPoint(it * 60.0, 140 + 15 * sin(it / 6.0)) }.toImmutableList()

@PreviewTest
@PreviewComponentVariants
@Composable
private fun Charts() {
    ComponentPreview {
        TimeSeriesChart(
            points = heartRate,
            xRange = 0.0..3600.0,
            description = "Heart rate",
            yLabel = { "${it.toInt()}" },
            xLabel = { "${(it / 60).toInt()}:00" },
            markerLabel = { _, y -> "${y.toInt()} bpm" },
            average = 140.0,
        )
        ZoneBars(
            persistentListOf(
                ZoneBar("Z1", "Recovery", "< 120 bpm", "5:00", 0.08f),
                ZoneBar("Z2", "Endurance", "120–145 bpm", "35:00", 0.58f),
                ZoneBar("Z3", "Tempo", "145–160 bpm", "15:00", 0.25f),
                ZoneBar("Z4", "Threshold", "160–175 bpm", "5:00", 0.08f),
                ZoneBar("Z5", "Anaerobic", "> 175 bpm", "0:10", 0.003f),
            )
        )
    }
}
