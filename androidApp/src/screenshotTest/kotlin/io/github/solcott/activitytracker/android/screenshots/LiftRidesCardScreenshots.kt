package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.detail.LiftRidesCard

/** A resort day's lifts, each with its rides and time on it. */
@PreviewTest
@PreviewComponentVariants
@Composable
private fun LiftRidesCardResortDay() {
    ComponentPreview { LiftRidesCard(ActivityDetailSamples.liftRides, onOpen = {}) }
}
