package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import com.slack.circuit.subcircuit.SubUi
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.preview.PreviewSubUis
import io.github.solcott.activitytracker.data.activity.UploadSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.TimeZone

/** The feed's upload rows (data/outbox): activities uploading, imported apart, other changes, refused ones and the pauses. */
@OptIn(ExperimentalTestApi::class)
class FeedUploadsUiTest {
    @Test
    fun theRowCountsActivitiesAndOpensTheQueue() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview(subUis = FilterPlaceholder) { FeedUi(FeedSamples.unsent.copy(eventSink = { events += it })) } }
        onNodeWithText("Uploading 1 activity and 950 imported · 3 other changes").performClick()
        onNodeWithText("2 changes couldn't be published").performClick()
        assertEquals(listOf<FeedEvent>(FeedEvent.OpenUploads, FeedEvent.OpenUploads), events)
    }

    @Test
    fun otherChangesAloneAreCountedAsChanges() = runComposeUiTest {
        val state = FeedSamples.unsent.copy(uploads = UploadSummary(other = 3))
        setContent { AppPreview(subUis = FilterPlaceholder) { FeedUi(state) } }
        onNodeWithText("Uploading 3 changes…").assertExists()
    }

    @Test
    fun importsTheAthletePausedAreWaiting() = runComposeUiTest {
        val state = FeedSamples.unsent.copy(uploads = UploadSummary(imported = 900, importsPaused = true))
        setContent { AppPreview(subUis = FilterPlaceholder) { FeedUi(state) } }
        onNodeWithText("900 imported activities waiting").assertExists()
        onNodeWithText("Imports paused").assertExists()
    }

    @Test
    fun theFeedSaysUntilWhenTheServerPausedUploads() = runComposeUiTest {
        setContent {
            AppPreview(subUis = FilterPlaceholder, now = Fixed.NOW, timeZone = TimeZone.UTC, use24Hour = false) {
                FeedUi(FeedSamples.paused)
            }
        }
        onNodeWithText("Uploading 3000 imported activities").assertExists()
        onNodeWithText("Paused by the server until 3:40 PM").assertExists()
    }

    @Test
    fun aPauseEndingAfterMidnightSaysTomorrow() = runComposeUiTest {
        val state = FeedSamples.paused.copy(uploads = FeedSamples.paused.uploads.copy(serverPausedUntil = Fixed.NOW + 13.hours))
        setContent { AppPreview(subUis = FilterPlaceholder, now = Fixed.NOW, timeZone = TimeZone.UTC) { FeedUi(state) } }
        onNodeWithText("Paused by the server until tomorrow at 01:00").assertExists()
    }

    /**
     * Stands in for the feed filter, whose UI is another feature's: it keeps the filter's place, and with it the top bar's padding, so the
     * list starts below the bar as in the app.
     */
    private object FilterPlaceholder : PreviewSubUis {
        override fun ui(screen: SubScreen<*>): SubUi<*> = SubUi<SubCircuitUiState> { _, modifier -> Spacer(modifier) }

        override fun defaultState(screen: SubScreen<*>): SubCircuitUiState? = null
    }
}
