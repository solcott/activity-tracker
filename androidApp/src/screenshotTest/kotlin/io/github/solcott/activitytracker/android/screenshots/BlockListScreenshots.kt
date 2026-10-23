package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.profile.BlockListSamples
import io.github.solcott.activitytracker.app.profile.BlockListState
import io.github.solcott.activitytracker.app.profile.BlockListUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun BlockListLoaded() {
    ScreenPreview { BlockListUi(BlockListSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun BlockListStates(@PreviewParameter(BlockListStates::class) state: BlockListState) {
    ScreenPreview { BlockListUi(state) }
}

class BlockListStates : OtherSamples<BlockListState>(BlockListSamples.all)
