package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The All data page (feed/activity-page-layout): it opens at the section it was asked for, and its back button leaves. */
@OptIn(ExperimentalTestApi::class)
class ActivityDataUiTest {
    @Test
    fun backLeavesThePage() = runComposeUiTest {
        val events = mutableListOf<ActivityDataEvent>()
        val state = ActivityDataSamples.atLaps.copy(eventSink = { events += it })
        setContent { AppPreview { ActivityDataUi(state) } }
        onAllNodesWithTag(TestTags.BACK, useUnmergedTree = true)[0].performClick()
        assertEquals(listOf<ActivityDataEvent>(ActivityDataEvent.Back), events)
    }

    @Test
    fun itOpensAtATableThatLoadsAfterThePage() = runComposeUiTest {
        // The activity loads first, its tables only once its streams do.
        val full = ActivityDataSamples.data.copy(start = DataSection.Laps)
        var data by mutableStateOf(full.copy(charts = null))
        lateinit var listState: LazyListState
        setContent {
            AppPreview {
                listState = rememberLazyListState()
                ActivityDataList(data, listState, PaddingValues(), onOpenGear = {}, modifier = Modifier.height(400.dp))
            }
        }
        waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "no Laps section to open at yet")
        data = full
        waitForIdle()
        assertEquals(full.sections.indexOfFirst { it.section == DataSection.Laps }, listState.firstVisibleItemIndex)
    }
}
