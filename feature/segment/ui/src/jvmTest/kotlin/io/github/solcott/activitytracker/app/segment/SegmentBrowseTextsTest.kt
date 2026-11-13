package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What segment browse lists and says, and a segment's credit (segments/segment-browse, dev/shared-display-text). */
class SegmentBrowseTextsTest {
    @Test
    fun theSportChipNamesEachGroupOrAll() {
        assertEquals(SegmentBrowseTexts.allSports, SegmentBrowseTexts.sportChoice(null))
        assertEquals(SegmentBrowseTexts.sport("ride"), SegmentBrowseTexts.sportChoice("ride"))
    }

    @Test
    fun theListShowsWhatTheBrowseStateCallsFor() {
        assertEquals(BrowseList.Area, SegmentBrowseSamples.loaded.listShows(mapSupported = true))
        assertEquals(BrowseList.WebPrompt, SegmentBrowseSamples.loaded.listShows(mapSupported = false))
        assertEquals(BrowseList.Names, SegmentBrowseSamples.nameResults.listShows(mapSupported = false))
        assertEquals(BrowseList.Locating, SegmentBrowseSamples.locating.listShows(mapSupported = true))
        assertEquals(BrowseList.Nowhere, SegmentBrowseSamples.nowhere.listShows(mapSupported = true))
        assertEquals(BrowseList.TooWide, SegmentBrowseSamples.tooWide.listShows(mapSupported = true))
        assertNull(SegmentBrowseTexts.message(BrowseList.Area))
        assertEquals(AppText.named("segment_browse_zoom_in"), SegmentBrowseTexts.message(BrowseList.TooWide))
    }

    @Test
    fun noMatchHereOffersSearchingEverywhere() {
        val here = SegmentBrowseSamples.nothingHere
        assertTrue(here.offersScope)
        assertTrue(here.offersSearchEverywhere)
        assertEquals(AppText.named("segment_browse_no_results_here", AppText.written("hog")), SegmentBrowseTexts.noResults(here))
        val everywhere = here.copy(scope = NameScope.Everywhere)
        assertFalse(everywhere.offersSearchEverywhere)
        assertEquals(AppText.named("segment_browse_no_results", AppText.written("hog")), SegmentBrowseTexts.noResults(everywhere))
        assertFalse(SegmentBrowseSamples.loaded.offersScope)
    }

    @Test
    fun aRunReadsItsLengthTypeAndHowFarAway() {
        val run = SegmentBrowseSamples.runs.first()
        val units = UnitSystem.Metric
        assertEquals(
            AppText.named(
                "segment_browse_details",
                AppText.named(
                    "segment_browse_details",
                    StatText.distance(run.segment.distanceMeters.toDouble(), units),
                    SegmentTexts.type(run.segment.record.sport, run.segment.record.kind),
                ),
                AppText.named("segment_browse_away", StatText.distance(run.metersAway.toDouble(), units)),
            ),
            SegmentBrowseTexts.details(run, units),
        )
    }

    @Test
    fun theMapDrawsTheSelectedRunLast() {
        val map = SegmentBrowseSamples.selected.segmentsMap
        assertEquals(SegmentBrowseSamples.selected.selected?.value, map.drawn.last().uri)
    }

    @Test
    fun everyNameTheBrowseAndCreditTextsChooseHasItsString() {
        val state = SegmentBrowseSamples.nothingHere
        val texts =
            BrowseList.entries.mapNotNull(SegmentBrowseTexts::message) +
                NameScope.entries.map(SegmentBrowseTexts::scope) +
                state.sports.map(SegmentBrowseTexts::sport) +
                SegmentBrowseSamples.runs.map { SegmentBrowseTexts.details(it, UnitSystem.Imperial) } +
                listOf(
                    SegmentBrowseTexts.searchLabel,
                    SegmentBrowseTexts.allSports,
                    SegmentBrowseTexts.capped,
                    SegmentBrowseTexts.noneHere,
                    SegmentBrowseTexts.noResults(state),
                    SegmentBrowseTexts.noResults(state.copy(scope = NameScope.Everywhere)),
                    SegmentBrowseTexts.searchEverywhere,
                ) +
                listOf(SegmentCredit.OpenStreetMap("ODbL"), SegmentCredit.Agency("PD", "USFS"), SegmentCredit.Agency("PD"))
                    .flatMap(SegmentCreditTexts::lines)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names) + listOfNotNull(text.fallback).flatMap(::names)
            else -> emptyList()
        }
}
