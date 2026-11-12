package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteConditions
import io.github.solcott.activitytracker.app.fixtures.fakeRegularEntry
import io.github.solcott.activitytracker.app.names.PlaceNameEvent
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.app.names.PlaceNameTexts
import io.github.solcott.activitytracker.app.segment.PlaceTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone

/**
 * Every string the dive site page's and a place's names section's texts choose exists, and they read as both UIs show them
 * (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts.
 */
class DiveSiteTextNamesTest {
    private val renamed = DiveSiteSamples.renamed
    private val site = checkNotNull(renamed.site)
    private val bobs = renamed.proposals.first()
    private val alices = renamed.proposals.last()

    @Test
    fun everyNameThePageChoosesHasItsString() {
        val conditions = fakeDiveSiteConditions()
        val statuses = listOf(SegmentStatus.PROPOSED, SegmentStatus.DUPLICATE, SegmentStatus.ARCHIVED).map { site.copy(status = it) }
        val texts =
            UnitSystem.entries.flatMap { units ->
                listOf(DiveSiteTexts.radius(site, units)) +
                    DiveSiteTexts.conditionLines(conditions, units) +
                    DiveSiteTexts.monthLines(conditions, units)
            } +
                statuses.flatMap { listOfNotNull(DiveSiteTexts.status(it), DiveSiteTexts.statusHelp(it)) } +
                listOfNotNull(
                    DiveSiteTexts.createdBy(site),
                    DiveSiteTexts.osmCredit(checkNotNull(DiveSiteSamples.proposed.site)),
                    DiveSiteTexts.license(checkNotNull(DiveSiteSamples.proposed.site)),
                    DiveSiteTexts.regularsTitle,
                    DiveSiteTexts.regularsNone,
                    DiveSiteTexts.yourVisits(fakeRegularEntry(rank = 4, visits = 6)),
                    DiveSiteTexts.conditionsTitle,
                    DiveSiteTexts.conditionsFrom(conditions),
                    DiveSiteTexts.dives,
                    DiveSiteTexts.noDives,
                    PlaceTexts.openOriginal,
                    PlaceNameTexts.originally("Ras Mohammed"),
                    PlaceNameTexts.propose,
                    PlaceNameTexts.proposals,
                    PlaceNameTexts.history,
                    PlaceNameTexts.edit,
                    PlaceNameTexts.delete,
                    PlaceNameTexts.votingUnavailable,
                    PlaceNameTexts.proposedBy(bobs),
                    PlaceNameTexts.voteUp(bobs),
                    PlaceNameTexts.voteDown(bobs),
                    PlaceNameTexts.historyEntry(renamed.history.single(), TimeZone.UTC),
                    PlaceNameTexts.historyBy(renamed.history.single()),
                )
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun conditionsReadInTheViewersUnits() {
        val metric = DiveSiteTexts.conditionLines(fakeDiveSiteConditions(), UnitSystem.Metric).first() as AppText.Named
        val imperial = DiveSiteTexts.conditionLines(fakeDiveSiteConditions(), UnitSystem.Imperial).first() as AppText.Named
        assertEquals("dive_site_conditions_temperature", metric.name)
        assertTrue(names(metric).contains("unit_temperature_c"))
        assertTrue(names(imperial).contains("unit_temperature_f"))
        assertEquals(2, DiveSiteTexts.monthLines(fakeDiveSiteConditions(), UnitSystem.Metric).size)
        assertTrue(DiveSiteTexts.monthLines(fakeDiveSiteConditions().copy(months = emptyList()), UnitSystem.Metric).isEmpty())
    }

    @Test
    fun aDuplicateOffersItsOriginalAndAnActiveSiteSaysNothing() {
        assertTrue(DiveSiteTexts.offersOriginal(checkNotNull(DiveSiteSamples.duplicate.site)))
        assertFalse(DiveSiteTexts.offersOriginal(site))
        assertNull(DiveSiteTexts.statusHelp(site))
        assertNull(DiveSiteTexts.osmCredit(site))
    }

    @Test
    fun aVoteTapSendsItsDirectionOrTakesTheVoteBack() {
        // Bob's proposal carries the viewer's vote for it.
        assertEquals(PlaceNameEvent.Vote(bobs.uri, null), PlaceNameTexts.voteEvent(bobs, up = true))
        assertEquals(PlaceNameEvent.Vote(bobs.uri, SegmentNameVoteDirection.DOWN), PlaceNameTexts.voteEvent(bobs, up = false))
        assertTrue(PlaceNameTexts.votedUp(bobs))
        assertFalse(PlaceNameTexts.votedDown(bobs))
        assertTrue(renamed.names.isOwn(alices))
        assertFalse(renamed.names.isOwn(bobs))
    }

    @Test
    fun totalsAreGroupedAndAnEmptySectionTakesNoSpace() {
        val entry = PlaceNameTexts.historyEntry(renamed.history.single(), TimeZone.UTC) as AppText.Named
        assertTrue(entry.args.contains(AppText.written(formatWhole(1_204))), "${entry.args}")
        assertFalse(PlaceNameTexts.shows(0, 0, canPropose = false, PlaceNameFields()))
        assertTrue(PlaceNameTexts.shows(0, 0, canPropose = true, PlaceNameFields()))
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Written,
            is AppText.Time -> emptyList()
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
        }
}
