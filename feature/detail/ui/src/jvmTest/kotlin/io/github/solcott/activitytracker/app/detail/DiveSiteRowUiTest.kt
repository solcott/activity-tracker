package io.github.solcott.activitytracker.app.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSite
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteBasic
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals

/** The dive page's site (dive-sites/site-visits): the visited site, opening it, or the logbook's own site text when none is visible. */
@OptIn(ExperimentalTestApi::class)
class DiveSiteRowUiTest {
    private fun page(site: DiveSiteViewBasic?, events: MutableList<ActivityDetailEvent>) =
        ActivityDetailSamples.page.let { page ->
            val record = page.activity.record.copy(sport = Sport.DIVE, dive = ActivityDive(site = "Ras Mo"))
            page.copy(activity = page.activity.copy(record = record, diveSite = site), eventSink = { events += it })
        }

    @Test
    fun aVisitedSiteShowsItsNameAndStatusAndOpens() = runComposeUiTest {
        val events = mutableListOf<ActivityDetailEvent>()
        val site = fakeDiveSiteBasic(fakeDiveSite(name = "Ras Mohammed", status = SegmentStatus.PROPOSED))
        setContent { AppPreview { ActivityDetailUi(ActivityDetailState(ContentStates.loaded(page(site, events)), eventSink = {})) } }
        onNodeWithText("Ras Mohammed · Proposed").performClick()
        onNodeWithText("Ras Mo").assertDoesNotExist()
        assertEquals(listOf<ActivityDetailEvent>(ActivityDetailEvent.OpenDiveSite), events)
    }

    @Test
    fun withNoVisibleVisitTheLogbooksSiteShows() = runComposeUiTest {
        val events = mutableListOf<ActivityDetailEvent>()
        setContent { AppPreview { ActivityDetailUi(ActivityDetailState(ContentStates.loaded(page(null, events)), eventSink = {})) } }
        onNodeWithText("Ras Mo").performClick()
        assertEquals(emptyList(), events, "the logbook's text opens nothing")
    }
}
