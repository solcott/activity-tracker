package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.detail.SegmentEffortsCard
import kotlinx.collections.immutable.toImmutableList

/** A whole trail over its two stretches. */
@PreviewTest
@PreviewComponentVariants
@Composable
private fun SegmentEffortsCardStretches() {
    ComponentPreview { SegmentEffortsCard(ActivityDetailSamples.nestedSegments.take(1).toImmutableList(), onOpen = {}) }
}

/** A lone effort, then parent trails nested two deep. */
@PreviewTest
@PreviewComponentVariants
@Composable
private fun SegmentEffortsCardParentTrails() {
    ComponentPreview { SegmentEffortsCard(ActivityDetailSamples.nestedSegments.drop(1).toImmutableList(), onOpen = {}) }
}
