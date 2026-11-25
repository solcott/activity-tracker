package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDataEvent
import io.github.solcott.activitytracker.app.detail.ActivityDataPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDataScreen
import io.github.solcott.activitytracker.app.detail.DataSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The All data page (feed/activity-page-layout): the activity's stats as its page shows them, opened at a section. */
class ActivityDataPresenterTest : ActivityTestBase() {
    @Test
    fun itShowsTheActivitysStatsAtTheSectionItWasOpenedAt() = runTest {
        val r = repos()
        val navigator = FakeNavigator(ActivityDataScreen(activityUri, "Laps"))
        presenterTestOf(
            { ActivityDataPresenter(ActivityDataScreen(activityUri, "Laps"), navigator, r.feed, r.profiles, r.units, r.segments) },
        ) {
            val state = awaitUntil { it.content.data != null }
            val data = state.content.data!!
            assertEquals(DataSection.Laps, data.start)
            assertTrue(data.stats.isNotEmpty(), "the served activity's figures")
            state.eventSink(ActivityDataEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
