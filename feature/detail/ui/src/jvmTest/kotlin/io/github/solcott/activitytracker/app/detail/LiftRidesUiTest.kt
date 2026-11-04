package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf

/** The activity page's Lifts card (segments/segment-lifts): each lift once, with its rides and time, and no PR or rank. */
@OptIn(ExperimentalTestApi::class)
class LiftRidesUiTest {
    @Test
    fun aResortDayListsEachLiftWithItsRides() = runComposeUiTest {
        val events = mutableListOf<ActivityDetailEvent>()
        val page = ActivityDetailSamples.page.copy(lifts = ActivityDetailSamples.liftRides, eventSink = { events += it })
        setContent { AppPreview { LazyColumn { lifts(page) } } }
        onNodeWithText("Lifts").assertExists()
        onNodeWithText("3 rides").assertExists()
        onNodeWithText("1 ride").assertExists()
        onNodeWithText("PR").assertDoesNotExist()
        onNodeWithText("Summit Quad").performClick()
        assertEquals(ActivityDetailSamples.liftRides[1].segment, assertIs<ActivityDetailEvent.SegmentOpened>(events.single()).uri)
    }

    @Test
    fun aDayWithoutLiftsHasNoCard() = runComposeUiTest {
        setContent { AppPreview { LazyColumn { lifts(ActivityDetailSamples.page.copy(lifts = persistentListOf())) } } }
        onNodeWithText("Lifts").assertDoesNotExist()
    }
}
