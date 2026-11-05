package io.github.solcott.activitytracker.app.divesite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The dive site screen (dive-sites), its Regulars board and creating a site, from their samples. */
@OptIn(ExperimentalTestApi::class)
class DiveSiteUiTest {
    @Test
    fun aSiteShowsItsRadiusRegularsConditionsAndDives() = runComposeUiTest {
        val events = mutableListOf<DiveSiteEvent>()
        setContent { AppPreview { DiveSiteUi(DiveSiteSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("Within 100 m of its centre").assertExists()
        onNodeWithText("Top regular · all time").assertExists()
        onNodeWithText("31 dives").performClick()
        assertEquals(DiveSiteEvent.OpenRegulars, events.single())
        val list = onAllNodes(hasScrollAction()).onFirst()
        list.performScrollToNode(hasText("From 42 dives"))
        onNodeWithText("Water 26.5 °C (24.0 °C to 29.0 °C)").assertExists()
        onNodeWithText("Max depth 18.0 m, deepest 31.0 m").assertExists()
        list.performScrollToNode(hasText("Blue Hole wall"))
        onNodeWithText("Blue Hole wall").performClick()
        assertIs<DiveSiteEvent.OpenDive>(events.last())
    }

    @Test
    fun conditionsShowInTheViewersUnits() = runComposeUiTest {
        setContent { AppPreview(units = UnitSystem.Imperial) { DiveSiteUi(DiveSiteSamples.loaded) } }
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("From 42 dives"))
        onNodeWithText("Water 79.7 °F", substring = true).assertExists()
        onNodeWithText("Max depth 59.1 ft", substring = true).assertExists()
    }

    @Test
    fun anOsmSiteIsCreditedAndAProposedOneSaysSo() = runComposeUiTest {
        setContent { AppPreview { DiveSiteUi(DiveSiteSamples.proposed) } }
        onNodeWithText("© OpenStreetMap contributors").assertExists()
        onNodeWithText("Data licence: ODbL-1.0").assertExists()
        onNodeWithText("Proposed").assertExists()
        onNodeWithText("Top regular · all time").assertDoesNotExist()
    }

    @Test
    fun theRegularsBoardRanksDivers() = runComposeUiTest {
        val events = mutableListOf<DiveSiteRegularsEvent>()
        setContent { AppPreview { DiveSiteRegularsUi(DiveSiteRegularsSamples.all.getValue("end").copy(eventSink = { events += it })) } }
        onNodeWithText("Regulars of Blue Hole").assertExists()
        onNodeWithText("31 dives").assertExists()
        onNodeWithText("Following").performClick()
        assertEquals(DiveSiteRegularsEvent.ScopeChanged("following"), events.single())
    }

    @Test
    fun creatingASiteWarnsItsPublicAndListsADuplicate() = runComposeUiTest {
        val events = mutableListOf<CreateDiveSiteEvent>()
        setContent {
            AppPreview {
                CreateDiveSiteUi(CreateDiveSiteSamples.all.getValue(CreateDiveSiteSamples.DUPLICATE).copy(eventSink = { events += it }))
            }
        }
        onNodeWithText("Radius: 100 m").assertExists()
        onNodeWithText("will be public", substring = true).assertExists()
        onNodeWithText("Ras Mohammed Shark Reef").performClick()
        assertIs<CreateDiveSiteEvent.OpenSite>(events.single())
    }

    @Test
    fun aDiveInsideAZoneSaysWhy() = runComposeUiTest {
        setContent { AppPreview { CreateDiveSiteUi(CreateDiveSiteSamples.all.getValue(CreateDiveSiteSamples.INSIDE_ZONE)) } }
        onNodeWithText("inside one of your privacy zones", substring = true).assertExists()
        onNodeWithText("Publish site").assertDoesNotExist()
    }
}
