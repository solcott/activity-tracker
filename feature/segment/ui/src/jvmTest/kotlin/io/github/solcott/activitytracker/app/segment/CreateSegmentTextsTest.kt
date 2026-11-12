package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the create-segment form says, for the shared and the native form (segments/segment-creation, dev/shared-display-text). */
class CreateSegmentTextsTest {
    private val loaded = checkNotNull(CreateSegmentSamples.all[SampleKind.LOADED])
    private val ride = SegmentTexts.sportGroup("ride")

    @Test
    fun anOkStretchReadsItsLengthAndSportGroup() {
        val units = UnitSystem.Imperial
        assertEquals(
            AppText.named("segment_create_length", StatText.distance(960.0, units), ride),
            CreateSegmentTexts.selection(loaded, units),
        )
        assertFalse(CreateSegmentTexts.selectionFails(loaded))
    }

    @Test
    fun aRefusedStretchSaysWhyAndFails() {
        val short = checkNotNull(CreateSegmentSamples.all[SampleKind.INVALID])
        assertEquals(
            AppText.named("segment_create_too_short", ride, StatText.distance(200.0, UnitSystem.Metric)),
            CreateSegmentTexts.selection(short, UnitSystem.Metric),
        )
        assertTrue(CreateSegmentTexts.selectionFails(short))
        val gap = checkNotNull(CreateSegmentSamples.variants["crossesGap"])
        assertEquals(AppText.named("segment_create_crosses_gap"), CreateSegmentTexts.selection(gap, UnitSystem.Metric))
        assertTrue(CreateSegmentTexts.selectionFails(gap))
    }

    @Test
    fun thePublicNoteLinksTheActivityOnlyWhenItWill() {
        assertEquals(1, CreateSegmentTexts.publicNote(namesActivity = false).size)
        assertEquals(AppText.named("segment_create_links_activity"), CreateSegmentTexts.publicNote(namesActivity = true).last())
    }

    @Test
    fun theMapHighlightsTheChosenStretch() {
        assertNotNull(assertNotNull(loaded.routeMap).highlight)
        assertNull(assertNotNull(checkNotNull(CreateSegmentSamples.all[SampleKind.INVALID]).routeMap).highlight)
        assertNull(checkNotNull(CreateSegmentSamples.all[SampleKind.LOADING]).routeMap)
    }

    @Test
    fun everyNameTheFormChoosesHasItsString() {
        val states = CreateSegmentSamples.all.values + CreateSegmentSamples.variants.values
        val duplicate = checkNotNull(CreateSegmentSamples.variants["duplicate"]).duplicates.first()
        val texts =
            UnitSystem.entries.flatMap { units ->
                states.filter { it.sport != null }.map { CreateSegmentTexts.selection(it, units) } +
                    CreateSegmentTexts.duplicate(duplicate, units)
            } +
                CreateSegmentUnavailable.entries.map(CreateSegmentTexts::unavailable) +
                CreateSegmentTexts.publicNote(namesActivity = true) +
                CreateSegmentTexts.range +
                CreateSegmentTexts.startThumb +
                CreateSegmentTexts.endThumb +
                CreateSegmentTexts.along(480.0, UnitSystem.Metric)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names) + listOfNotNull(text.fallback).flatMap(::names)
            else -> emptyList()
        }
}
