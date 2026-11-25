package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDataScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.DataSection
import io.github.solcott.activitytracker.app.detail.DetailSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** Strips and All data (feed/activity-page-layout): which cards show open, and where All data opens. */
class ActivityDetailCardsTest : ActivityDetailTestBase() {
    @Test
    fun aStripOpensAndClosesInPlace() = runTest {
        detail(repos()) {
            val page = awaitLoaded()
            assertNull(page.openCards, "only the lead is open until the viewer chooses")
            page.eventSink(ActivityDetailEvent.ToggleCard(DetailSection.HeartRate))
            val opened = awaitUntil { it.loaded()?.openCards?.contains(DetailSection.HeartRate) == true }.loaded()!!
            opened.eventSink(ActivityDetailEvent.ToggleCard(DetailSection.HeartRate))
            awaitUntil { it.loaded()?.openCards?.contains(DetailSection.HeartRate) == false }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun allDataOpensInTheSidePaneAndCloses() = runTest {
        detail(repos()) {
            val page = awaitLoaded()
            page.eventSink(ActivityDetailEvent.OpenAllData(DataSection.Laps, inPane = true))
            val shown = awaitUntil { it.loaded()?.allData != null }.loaded()!!
            assertEquals(DataSection.Laps, shown.allData)
            shown.eventSink(ActivityDetailEvent.CloseAllData)
            awaitUntil { it.loaded()?.allData == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutASidePaneAllDataIsAPageOfItsOwn() = runTest {
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenterOf(repos(), navigator = navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.OpenAllData(DataSection.Laps, inPane = false))
            assertEquals(ActivityDataScreen(activityUri, "Laps"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
