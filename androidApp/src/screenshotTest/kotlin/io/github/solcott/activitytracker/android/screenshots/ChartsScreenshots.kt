package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.chart.LineChartSamples
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import kotlinx.collections.immutable.persistentListOf

@PreviewTest
@PreviewComponentVariants
@Composable
private fun Charts() {
    ComponentPreview {
        // The shared chart: a heart rate line with the elevation behind it and its average, a pace line drawn inverted.
        LineChartUi(LineChartSamples.heartRate)
        LineChartUi(LineChartSamples.pace)
        ZoneBars(
            persistentListOf(
                ZoneBar("Z1", "Recovery", "< 120 bpm", "5:00", 0.08f),
                ZoneBar("Z2", "Endurance", "120–145 bpm", "35:00", 0.58f),
                ZoneBar("Z3", "Tempo", "145–160 bpm", "15:00", 0.25f),
                ZoneBar("Z4", "Threshold", "160–175 bpm", "5:00", 0.08f),
                ZoneBar("Z5", "Anaerobic", "> 175 bpm", "0:10", 0.003f),
            ),
        )
    }
}
