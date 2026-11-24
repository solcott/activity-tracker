package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeTrailAttributes
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail as Detail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the Segment and Trail pages say about riders' trail details (segments/trail-detail-suggestions, segments/trail-attributes). */
class TrailContributionTextsTest {
    private val community = SegmentSamples.trailCommunity.segment!!

    private fun rows() = TrailContributionTexts.groups(community, rider = true).flatMap { it.rows }

    private fun row(key: String) = rows().single { it.key == key }

    @Test
    fun rowsSayWhereEachValueCameFrom() {
        assertEquals(AppText.named("trail_source_osm"), row(Detail.SURFACE).source)
        assertEquals(AppText.named("trail_source_usfs"), row(Detail.WIDTH).source)
        val dogs = row(Detail.DOG)
        assertTrue(dogs.community)
        assertEquals(
            AppText.Plural("trail_source_community", 12, kotlinx.collections.immutable.persistentListOf(AppText.written("12"))),
            dogs.source,
        )
        assertEquals(AppText.named("trail_may_have_changed"), row(Detail.LIT).note, "a doubt outranks the suggestion count")
    }

    @Test
    fun onlyChangeableDetailsOpenAndOnlyForARider() {
        assertTrue(row(Detail.DOG).opens)
        assertTrue(!row(Detail.TRAIL_CLASS).opens, "official")
        assertTrue(!row(Detail.OPERATOR).opens)
        assertTrue(TrailContributionTexts.groups(community, rider = false).flatMap { it.rows }.none { it.opens })
    }

    @Test
    fun aSuggestedOnlyDetailIsAQuestionInItsCard() {
        val bare =
            community.copy(trail = TrailAttributes(dog = TrailDog.YES), trailDetails = listOf(TrailDetailInfo(Detail.DOG, TrailSource.OSM)))
        val groups = TrailContributionTexts.groups(bare, rider = true)
        val lit = groups.flatMap { it.rows }.single { it.key == Detail.LIT }
        assertTrue(lit.question)
        assertEquals(AppText.named("trail_pending_question", AppText.named("trail_lit_no")), lit.value)
        assertEquals(AppText.named("trail_pending_progress", AppText.written("4")), lit.note)
        assertEquals(AppText.named("trail_group_access"), groups.single { lit in it.rows }.title)
    }

    @Test
    fun theLastConfirmedMonthIsTheLatest() {
        assertEquals(
            AppText.named(
                "trail_last_confirmed",
                AppText.named("trail_month_year", AppText.named("month_short_11"), AppText.written("2026")),
            ),
            TrailContributionTexts.lastConfirmed(community),
        )
    }

    @Test
    fun theSheetListsSuggestionsAndOffersChange() {
        val sheet = assertNotNull(TrailContributionTexts.sheet(community, Detail.LIT, Fixed.ALICE.value))
        assertEquals(AppText.named("trail_lit_yes"), sheet.value)
        assertEquals(1, sheet.suggestions.size)
        assertTrue(sheet.canConfirm && sheet.canSuggest)
        assertNull(TrailContributionTexts.sheet(community, Detail.HORSE + "x", null))
    }

    @Test
    fun thePickerMarksTheCurrentAndSuggestedValues() {
        val picker = TrailContributionTexts.picker(community, Detail.LIT)
        assertEquals(3, picker.options.size)
        assertTrue(picker.options[0].current, "lit yes is in effect")
        assertTrue(picker.options[2].suggested, "Maya suggested not lit")
        val width = TrailContributionTexts.picker(community, Detail.WIDTH)
        assertTrue(width.options[0].current, "60 cm reads as singletrack")
    }

    @Test
    fun missingDetailsFitTheSportAndSkipHeldOnes() {
        val keys = TrailContributionTexts.missing(SegmentSamples.trailInvite.segment).map { it.key }
        assertTrue(Detail.SURFACE in keys && Detail.DOG in keys)
        assertTrue(Detail.PISTE_DIFFICULTY !in keys, "a run segment shows no piste scale")
        assertTrue(Detail.WEBSITE !in keys)
        assertTrue(TrailContributionTexts.missing(community).none { it.key == Detail.DOG })
    }

    @Test
    fun theTrailPageSaysWhereSegmentsVary() {
        val path = fakePath().copy(trail = fakeTrailAttributes())
        val groups = TrailContributionTexts.pathGroups(path, listOf(community))
        val dogs = groups.flatMap { it.rows }.single { it.key == Detail.DOG }
        assertEquals(AppText.named("trail_varies"), dogs.value)
        assertEquals(listOf(community.currentName), dogs.varying.map { it.name })
        val summary = TrailContributionTexts.pathSummary(path, listOf(community))
        assertTrue(summary.toString().contains("trail_vary_dog"), "Dogs vary in the folded line")
        val same = TrailContributionTexts.pathGroups(path.copy(trail = fakeTrailAttributes().copy(dog = TrailDog.NO)), listOf(community))
        assertTrue(same.flatMap { it.rows }.none { it.varying.isNotEmpty() }, "the same value adopted doesn't vary")
    }

    @Test
    fun everyNewTextHasItsString() {
        val texts =
            rows().flatMap { listOfNotNull(it.label, it.value, it.source, it.note) } +
                listOfNotNull(TrailContributionTexts.lastConfirmed(community)) +
                Detail.suggestable.flatMap { key ->
                    TrailContributionTexts.options(key).map { TrailContributionTexts.valueText(key, it) } +
                        TrailContributionTexts.picker(community, key).title +
                        TrailTexts.labelOf(key)
                } +
                listOf(TrailSource.OSM, TrailSource.USFS, TrailSource.NPS, TrailSource.BLM, TrailSource.IMPORTED).map {
                    TrailContributionTexts.sourceText(it, null)
                } +
                listOfNotNull(TrailContributionTexts.sheet(community, Detail.LIT, null)?.source) +
                TrailContributionTexts.pathGroups(fakePath().copy(trail = fakeTrailAttributes()), listOf(community))
                    .flatMap { it.rows }
                    .mapNotNull { it.note } +
                listOf(Detail.SURFACE, Detail.WIDTH, Detail.DOG, Detail.SEASON).map { AppText.named("trail_vary_$it") } +
                listOf(
                        "trail_was_it_right",
                        "trail_still_right",
                        "trail_changed",
                        "trail_suggested_heading",
                        "trail_agree",
                        "trail_disagree",
                        "trail_votes_private",
                        "trail_suggest_different",
                        "trail_suggest_send",
                        "trail_suggest_current",
                        "trail_suggest_already",
                        "trail_suggest_public",
                        "trail_add_missing",
                        "trail_empty_title",
                        "trail_empty_body",
                        "trail_quick_title",
                        "trail_quick_body",
                        "trail_quick_all",
                        "trail_quick_later",
                        "trail_answer_still",
                        "trail_answer_changed",
                        "error_suggest_trail_detail",
                        "error_vote_trail_detail",
                    )
                    .map { AppText.named(it) }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
        assertTrue(fakeSegment().trailDetails.isEmpty())
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
