package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The segment screen credits the data its line came from (segments/segment-records). */
@OptIn(ExperimentalTestApi::class)
class SegmentCreditUiTest {
    private val opened = mutableListOf<String>()
    private val uriHandler =
        object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }

    private fun state(source: String, license: String, credit: SegmentCredit?) =
        SegmentState(
            segment =
                SegmentView(
                    uri = AtUri("at://did:plc:importer/${Nsid.FEED_SEGMENT}/3kshill"),
                    cid = Cid("bafyseg"),
                    creator = ProfileViewBasic(Did("did:plc:importer"), Handle("segments.test")),
                    record =
                        SegmentRecord(
                            name = "Ridge Trail",
                            sport = "ride",
                            line = "_p~iF~ps|U",
                            source = source,
                            license = license,
                            createdAt = Instant.parse("2026-10-21T00:00:00Z"),
                        ),
                    distanceMeters = 1000,
                ),
            isLoading = false,
            credit = credit,
        )

    private fun show(state: SegmentState, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalUriHandler provides uriHandler) { SegmentUi(state) } }
        checks()
    }

    @Test
    fun anOsmSegmentLinksToOsmsCopyrightPage() =
        show(state("osm", "ODbL-1.0", SegmentCredit.OpenStreetMap("ODbL-1.0"))) {
            onNodeWithText("Data licence: ODbL-1.0").assertExists()
            onNodeWithText("© OpenStreetMap contributors").performClick()
            assertEquals(listOf("https://www.openstreetmap.org/copyright"), opened)
        }

    @Test
    fun anAgencySegmentCreditsAgencyData() =
        show(state("agency", "CC0-1.0", SegmentCredit.Agency("CC0-1.0"))) {
            onNodeWithText("From public agency trail data").assertExists()
            onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
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
