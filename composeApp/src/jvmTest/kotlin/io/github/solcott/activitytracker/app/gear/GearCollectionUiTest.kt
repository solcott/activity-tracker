package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.record.GearCollectionOption
import io.github.solcott.activitytracker.app.record.GearEvent
import io.github.solcott.activitytracker.app.record.GearField
import io.github.solcott.activitytracker.app.record.GearOption
import io.github.solcott.activitytracker.app.record.GearPicker
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** Gear collections on the gear list, their own page and the gear picker (gear/gear-collections). */
@OptIn(ExperimentalTestApi::class)
class GearCollectionUiTest {
    @Test
    fun theGearListShowsCollectionsWithTheirItemCounts() = runComposeUiTest {
        val events = mutableListOf<GearListEvent>()
        val state = GearListSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        setContent { AppPreview { GearListUi(state) } }
        onNodeWithText("Collections").assertExists()
        onNodeWithText("4 items").assertExists()
        onNodeWithText("Race kit").performClick()
        onNodeWithText("New collection").performClick()
        assertEquals(listOf(GearListEvent.OpenCollection("3kcol1"), GearListEvent.AddCollection), events)
    }

    @Test
    fun aCollectionMarksItsRetiredWetsuit() = runComposeUiTest {
        setContent { AppPreview { GearCollectionUi(GearCollectionSamples.all.getValue(SampleKind.LOADED)) } }
        onNodeWithText("Cold water setup").assertExists()
        onNodeWithText("Wetsuit").assertExists()
        onNodeWithText("Retired", substring = true).assertExists()
    }

    @Test
    fun thePickerOffersCollectionsAndSaysWhatTheLimitLeftOut() = runComposeUiTest {
        val events = mutableListOf<GearEvent>()
        val field =
            GearField(
                options = persistentListOf(GearOption(TARMAC, "Tarmac", GearType.BIKE, visibility = Visibility.PUBLIC)),
                collections = persistentListOf(GearCollectionOption("3kit", "Race kit", 4)),
                notAdded = 2,
                eventSink = { events += it },
            )
        setContent { AppPreview { GearPicker(field) } }
        onNodeWithText("No gear").performClick()
        onNodeWithText("2 items weren't added: an activity lists at most 16.").assertExists()
        onNodeWithText("Add collection").performClick()
        onNodeWithText("Race kit · 4 items").performClick()
        assertEquals(listOf<GearEvent>(GearEvent.AddCollection("3kit")), events)
    }

    private companion object {
        val TARMAC = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3tarmac")
    }
}
