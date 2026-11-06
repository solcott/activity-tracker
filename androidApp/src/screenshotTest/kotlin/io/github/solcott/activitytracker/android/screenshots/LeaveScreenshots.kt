package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.LeaveSamples
import io.github.solcott.activitytracker.app.settings.LeaveState
import io.github.solcott.activitytracker.app.settings.LeaveUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun LeaveLoaded() {
    ScreenPreview { LeaveUi(LeaveSamples.all.loaded()) }
}

// The summary runs well below a phone's fold: a tall phone shows the places, device, export and confirm sections the states change.
@PreviewTest
@Preview(name = "tallPhone", widthDp = 400, heightDp = 2000)
@Composable
private fun LeaveSummaryStates(@PreviewParameter(LeaveSummaryStates::class) state: LeaveState) {
    ScreenPreview { LeaveUi(state) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun LeaveProgressStates(@PreviewParameter(LeaveProgressStates::class) state: LeaveState) {
    ScreenPreview { LeaveUi(state) }
}

class LeaveSummaryStates : OtherSamples<LeaveState>(LeaveSamples.all.filterValues { it.run == null })

class LeaveProgressStates : OtherSamples<LeaveState>(LeaveSamples.all.filterValues { it.run != null })
