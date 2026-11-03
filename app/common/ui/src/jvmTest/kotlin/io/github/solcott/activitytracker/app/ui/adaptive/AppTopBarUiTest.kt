package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTopBarUiTest {
    @Test
    fun showsBackBelowTheRoot() = runComposeUiTest {
        setContent { AppPreview { AppTopBar("Privacy zones", onBack = {}) } }
        onAllNodesWithTag(TestTags.BACK, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun hidesBackOnAPanesFirstPage() = runComposeUiTest {
        setContent { AppPreview { CompositionLocalProvider(LocalIsPaneRoot provides true) { AppTopBar("Privacy zones", onBack = {}) } } }
        onAllNodesWithTag(TestTags.BACK, useUnmergedTree = true).assertCountEquals(0)
    }
}
