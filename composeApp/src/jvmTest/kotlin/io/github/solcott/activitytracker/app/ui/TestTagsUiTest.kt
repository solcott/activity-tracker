package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.profile.BlockListSamples
import io.github.solcott.activitytracker.app.profile.BlockListUi
import io.github.solcott.activitytracker.app.segment.SegmentSamples
import io.github.solcott.activitytracker.app.segment.SegmentUi
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** Each shared loading piece, and the top bar's back button, carries its anchor (dev/ui-test-harness). */
@OptIn(ExperimentalTestApi::class)
class TestTagsUiTest {
    private fun segment(kind: String, tag: String) = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { SegmentUi(SegmentSamples.all.getValue(kind)) } }
        onNodeWithTag(tag, useUnmergedTree = true).assertExists()
    }

    private fun blocked(kind: String, tag: String) = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { BlockListUi(BlockListSamples.all.getValue(kind)) } }
        onNodeWithTag(tag, useUnmergedTree = true).assertExists()
    }

    @Test fun firstLoad() = segment(SampleKind.LOADING, TestTags.LOADING)

    @Test fun loadError() = segment(SampleKind.FAILED, TestTags.LOAD_ERROR)

    @Test fun retry() = segment(SampleKind.FAILED, TestTags.RETRY)

    @Test fun refreshing() = segment(SampleKind.REFRESHING, TestTags.REFRESHING)

    @Test fun refreshError() = segment(SampleKind.REFRESH_FAILED, TestTags.REFRESH_ERROR)

    @Test fun back() = segment(SampleKind.LOADED, TestTags.BACK)

    @Test fun pageLoading() = blocked(SampleKind.LOADING_MORE, TestTags.PAGE_LOADING)

    @Test fun pageError() = blocked(SampleKind.PAGE_FAILED, TestTags.PAGE_ERROR)

    @Test fun pageRetry() = blocked(SampleKind.PAGE_FAILED, TestTags.PAGE_RETRY)

    @Test fun listEnd() = blocked(SampleKind.END, TestTags.LIST_END)

    @Test fun pagedFirstLoad() = blocked(SampleKind.LOADING, TestTags.LOADING)

    @Test fun pagedRefreshError() = blocked(SampleKind.REFRESH_FAILED, TestTags.REFRESH_ERROR)
}
