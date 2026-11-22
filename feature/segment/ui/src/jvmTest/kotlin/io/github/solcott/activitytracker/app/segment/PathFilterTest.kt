package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.location.SegmentDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** How the Trail page searches, filters and sorts its rows (segments/path-filters). */
class PathFilterTest {
    @Suppress("LongParameterList") // A row's every field a test sets.
    private fun row(
        rkey: String,
        name: String,
        meters: Long,
        imba: Int? = null,
        gain: Long? = null,
        downhill: Boolean = false,
        sport: String = RIDE,
    ): SegmentGroupRow =
        SegmentGroupRow.groupOf(
            listOf(
                fakeSegment(rkey, record = fakeSegmentRecord(name, sport = sport), distanceMeters = meters)
                    .copy(
                        trail = imba?.let { TrailAttributes(mtbScaleImba = it) },
                        elevationGain = gain,
                        oneWay = downhill.takeIf { it },
                        climb = "downhill".takeIf { downhill },
                    ),
            ),
        )

    private val easy = row("easy", "Lupine Hiking ↔ Potato Hill", 1_200, imba = 1, gain = 40)
    private val blue = row("blue", "Ridge Line", 3_000, imba = 2, gain = 200, downhill = true)
    private val black = row("black", "Côte Noire", 7_000, imba = 3, gain = 500, downhill = true)
    private val unrated = row("own", "Alice's Loop", 2_500)

    private fun content(vararg rows: SegmentGroupRow, sections: List<PathListItem> = emptyList()) =
        PathContent(
            fakePath(sports = listOf(RIDE, HIKE)),
            rows.sortedByDescending { it.shown.distanceMeters }.toImmutableList(),
            sectioned = sections.toImmutableList(),
        )

    private val anns = content(easy, blue, black, unrated)

    private fun PathFilter.names(content: PathContent = anns, query: String = "", units: UnitSystem = UnitSystem.Metric) =
        apply(content, query, RIDE, units).groups.map { it.shown.currentName }

    @Test
    fun nothingChosenChangesNothing() {
        assertSame(anns, PathFilter().apply(anns, " ", RIDE, UnitSystem.Metric))
    }

    @Test
    fun searchFoldsCaseAndAccents() {
        assertEquals(listOf("Lupine Hiking ↔ Potato Hill"), PathFilter().names(query = "POTATO"))
        assertEquals(listOf("Lupine Hiking ↔ Potato Hill"), PathFilter().names(query = "pötato"))
        assertEquals(listOf("Côte Noire"), PathFilter().names(query = "cote"))
    }

    @Test
    fun levelsNarrowAndUnratedIsALevel() {
        val chosen = PathFilter().toggled(TrailRating.INTERMEDIATE).toggled(TrailRating.DIFFICULT)
        assertEquals(listOf("Côte Noire", "Ridge Line"), chosen.names())
        assertEquals(listOf("Alice's Loop"), PathFilter().toggled(null as TrailRating?).names())
    }

    @Test
    fun directionNarrows() {
        assertEquals(listOf("Côte Noire", "Ridge Line"), PathFilter().toggled(WayChoice(SegmentDirection.Downhill)).names())
    }

    @Test
    fun lengthBandsFollowTheUnits() {
        assertEquals(listOf("Lupine Hiking ↔ Potato Hill"), PathFilter().toggled(LengthBand.Short).names(units = UnitSystem.Imperial))
        assertEquals(listOf("Ridge Line", "Alice's Loop"), PathFilter().toggled(LengthBand.Medium).names(units = UnitSystem.Metric))
        assertEquals(LengthBand.Long, LengthBand.of(5_001, UnitSystem.Metric))
        assertEquals(LengthBand.Medium, LengthBand.of(4_828, UnitSystem.Imperial))
    }

    @Test
    fun sortsPutRowsWithNothingToSortByLast() {
        assertEquals(
            listOf("Lupine Hiking ↔ Potato Hill", "Ridge Line", "Côte Noire", "Alice's Loop"),
            PathFilter(sort = PathSort.EasiestFirst).names(),
        )
        assertEquals(
            listOf("Côte Noire", "Ridge Line", "Lupine Hiking ↔ Potato Hill", "Alice's Loop"),
            PathFilter(sort = PathSort.HardestFirst).names(),
        )
        assertEquals(
            listOf("Côte Noire", "Ridge Line", "Lupine Hiking ↔ Potato Hill", "Alice's Loop"),
            PathFilter(sort = PathSort.MostClimbing).names(),
        )
        assertEquals(
            listOf("Lupine Hiking ↔ Potato Hill", "Alice's Loop", "Ridge Line", "Côte Noire"),
            PathFilter(sort = PathSort.Shortest).names(),
        )
        assertEquals(
            listOf("Alice's Loop", "Côte Noire", "Lupine Hiking ↔ Potato Hill", "Ridge Line"),
            PathFilter(sort = PathSort.Name).names(),
        )
    }

    private val trees = row("t1", "Rush › Through the Trees Trail", 6_000, imba = 2)
    private val treesSpan = row("t2", "Rush › Through the Trees → Canyon Hollow", 4_000, imba = 3)
    private val treesShort = row("t3", "Rush - Through the Trees", 1_000, imba = 2)
    private val lime = row("lime", "Rush - Limelight", 1_500, imba = 1)

    private val rush =
        content(
            trees,
            treesSpan,
            treesShort,
            lime,
            sections =
                listOf(
                    PathListItem.Section(
                        "Through the Trees Trail",
                        persistentListOf(trees, treesSpan, treesShort),
                        SegmentGroupRow.groupOf(listOf(trees, treesSpan, treesShort).flatMap { it.members }),
                    ),
                    PathListItem.Group(lime),
                ),
        )

    @Test
    fun aSectionsNameMatchesAllItsRows() {
        val items = PathFilter().apply(rush, "trees trail", RIDE, UnitSystem.Metric).sectioned
        assertEquals(listOf("section:Through the Trees Trail"), items.map { it.key })
        assertEquals(3, (items.single() as PathListItem.Section).rows.size)
    }

    @Test
    fun aSectionLeftWithOneRowIsThatRow() {
        val items = PathFilter().toggled(TrailRating.DIFFICULT).apply(rush, "", RIDE, UnitSystem.Metric).sectioned
        assertEquals(listOf(PathListItem.Group(treesSpan)), items.toList())
    }

    @Test
    fun aSectionTakesItsFirstRowsPlaceUnderTheSort() {
        val items = PathFilter(sort = PathSort.Shortest).apply(rush, "", RIDE, UnitSystem.Metric).sectioned
        assertEquals(listOf("section:Through the Trees Trail", lime.shown.uri.value), items.map { it.key })
        assertEquals(treesShort, (items.first() as PathListItem.Section).rows.first())
    }

    @Test
    fun optionsShowOnlyWhenTheyNarrow() {
        val options = PathFilter.options(anns, RIDE, UnitSystem.Metric)
        assertEquals(listOf(RIDE, HIKE), options.sports)
        assertEquals(
            listOf(TrailRating.EASY, TrailRating.INTERMEDIATE, TrailRating.DIFFICULT, null),
            options.levels.map { it.rating },
        )
        assertEquals(listOf(WayChoice(SegmentDirection.Downhill)), options.ways)
        assertEquals(listOf(LengthBand.Short, LengthBand.Medium, LengthBand.Long), options.lengths.map { it.band })
        assertTrue(options.searchable)

        val few = PathFilter.options(content(easy, blue, black), RIDE, UnitSystem.Metric)
        assertFalse(few.searchable)
        assertTrue(few.levels.isEmpty() && few.ways.isEmpty() && few.lengths.isEmpty())
        assertEquals(listOf(RIDE, HIKE), few.sports)
    }

    @Test
    fun aChipNamesWhatItIsSetTo() {
        val easy = AppText.named("trail_rating_easy")
        val blue = AppText.named("trail_rating_intermediate")
        assertEquals(PathTexts.difficulty, PathTexts.filterChip(PathTexts.difficulty, emptyList()))
        assertEquals(easy, PathTexts.filterChip(PathTexts.difficulty, listOf(easy)))
        assertEquals(
            AppText.named("path_filter_more", easy, AppText.written("1")),
            PathTexts.filterChip(PathTexts.difficulty, listOf(easy, blue)),
        )
        assertEquals(PathTexts.difficulty, PathTexts.filterDescription(PathTexts.difficulty, PathTexts.difficulty))
        assertEquals(
            AppText.named("path_filter_described", PathTexts.difficulty, easy),
            PathTexts.filterDescription(PathTexts.difficulty, easy),
        )
    }

    @Test
    fun oneLevelOffersNoDifficulty() {
        val allEasy = content(easy, easy.copy(), row("e2", "Easy Two", 3_000, imba = 1), row("e3", "Easy Three", 6_000, imba = 1))
        assertTrue(PathFilter.options(allEasy, RIDE, UnitSystem.Metric).levels.isEmpty())
        val withUnrated = content(easy, row("e2", "Easy Two", 3_000, imba = 1), row("e3", "Easy Three", 6_000, imba = 1), unrated)
        assertEquals(listOf(TrailRating.EASY, null), PathFilter.options(withUnrated, RIDE, UnitSystem.Metric).levels.map { it.rating })
    }

    @Test
    fun pruningDropsChoicesNoRowHas() {
        val chosen = PathFilter(levels = persistentSetOf(TrailRating.EXTREME, TrailRating.EASY), lengths = persistentSetOf(LengthBand.Long))
        val pruned = chosen.pruned(PathFilter.options(anns, RIDE, UnitSystem.Metric))
        assertEquals<Set<TrailRating?>>(setOf(TrailRating.EASY), pruned.levels)
        assertEquals<Set<LengthBand>>(setOf(LengthBand.Long), pruned.lengths)
    }

    @Test
    fun clearingKeepsTheSort() {
        val chosen = PathFilter(levels = persistentSetOf(TrailRating.EASY), sort = PathSort.Name)
        assertEquals(PathFilter(sort = PathSort.Name), chosen.cleared())
    }

    @Test
    fun everyNameTheFiltersChooseHasItsString() {
        val texts =
            listOf(PathTexts.search, PathTexts.filters, PathTexts.sort, PathTexts.noMatches, PathTexts.clearFilters) +
                listOf(PathTexts.difficulty, PathTexts.direction, PathTexts.length).flatMap {
                    listOf(
                        PathTexts.filterChip(it, listOf(AppText.written("a"), AppText.written("b"))),
                        PathTexts.filterDescription(it, AppText.written("a")),
                    )
                } +
                PathFilter.sorts.map(PathTexts::sortChip) +
                (TrailRating.entries.map { LevelOption(it) } + LevelOption(null)).map(PathTexts::level) +
                (listOf(WayChoice()) + SegmentDirection.entries.map(::WayChoice)).map(PathTexts::way) +
                UnitSystem.entries.flatMap { units -> LengthBand.entries.map { PathTexts.length(LengthOption(it), units) } } +
                PathFilter.sorts.map(PathTexts::sort)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
