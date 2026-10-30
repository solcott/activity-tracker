package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.ui.preview.RecordingUriHandler
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals

/** The segment screen credits the data its line came from (segments/segment-records). */
@OptIn(ExperimentalTestApi::class)
class SegmentCreditUiTest {
    private val links = RecordingUriHandler()

    private fun state(source: String, license: String, credit: SegmentCredit?) =
        SegmentState(
            content =
                ContentStates.loaded(
                    fakeSegment(
                        creator = fakeProfileBasic(Did("did:plc:importer"), "segments.test"),
                        record = fakeSegmentRecord(source = source, license = license),
                    )
                ),
            credit = credit,
        )

    private fun show(state: SegmentState, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis, uriHandler = links) { SegmentUi(state) } }
        checks()
    }

    @Test
    fun anOsmSegmentLinksToOsmsCopyrightPage() =
        show(state("osm", "ODbL-1.0", SegmentCredit.OpenStreetMap("ODbL-1.0"))) {
            onNodeWithText("Data licence: ODbL-1.0").assertExists()
            onNodeWithText("© OpenStreetMap contributors").performClick()
            assertEquals(listOf("https://www.openstreetmap.org/copyright"), links.opened)
        }

    @Test
    fun anAgencySegmentCreditsAgencyData() =
        show(state("agency", "CC0-1.0", SegmentCredit.Agency("CC0-1.0"))) {
            onNodeWithText("From public agency trail data").assertExists()
            onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        }

    @Test
    fun anAgencySegmentNamesItsAgency() =
        show(state("agency", "CC0-1.0", SegmentCredit.Agency("CC0-1.0", "USDA Forest Service"))) {
            onNodeWithText("From USDA Forest Service trail data").assertExists()
            onNodeWithText("From public agency trail data").assertDoesNotExist()
        }

    @Test
    fun anUnknownSourceShowsItsLicence() =
        show(state("survey", "CC-BY-4.0", SegmentCredit.License("CC-BY-4.0"))) { onNodeWithText("Data licence: CC-BY-4.0").assertExists() }

    @Test
    fun anAthleteSegmentShowsOnlyItsCreator() =
        show(state("athlete", "CC0-1.0", null)) {
            onNodeWithText("segments.test", substring = true).assertExists()
            onNodeWithText("Data licence", substring = true).assertDoesNotExist()
        }
}
