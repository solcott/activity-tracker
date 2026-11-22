package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.text.searchKey
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.SegmentDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** How the Trail page orders its rows (segments/path-filters); [Longest] is its usual order. */
enum class PathSort {
    Longest,
    Shortest,
    Name,
    MostClimbing,
    EasiestFirst,
    HardestFirst,
}

/**
 * A length band of the Trail page's length filter (segments/path-filters): under 1 mi, 1 to 3 mi, over 3 mi in imperial; under 2 km, 2 to 5
 * km, over 5 km in metric.
 */
enum class LengthBand {
    Short,
    Medium,
    Long;

    companion object {
        private const val METERS_PER_MILE = 1609.344
        private const val IMPERIAL_SHORT_MILES = 1
        private const val IMPERIAL_LONG_MILES = 3
        private const val METRIC_SHORT_METERS = 2000.0
        private const val METRIC_LONG_METERS = 5000.0

        /** The band [meters] falls in, in [units]. */
        fun of(meters: Long, units: UnitSystem): LengthBand {
            val (short, long) =
                if (units == UnitSystem.Imperial) IMPERIAL_SHORT_MILES * METERS_PER_MILE to IMPERIAL_LONG_MILES * METERS_PER_MILE
                else METRIC_SHORT_METERS to METRIC_LONG_METERS
            return when {
                meters < short -> Short
                meters <= long -> Medium
                else -> Long
            }
        }
    }
}

/**
 * A choice of the Trail page's difficulty filter: a level, or Unrated when [rating] is null. A class, since Swift can't read enum lists.
 */
@Immutable data class LevelOption(val rating: TrailRating?)

/** A choice of the Trail page's length filter, a class for Swift as [LevelOption] is. */
@Immutable data class LengthOption(val band: LengthBand)

/** A choice of the Trail page's sort, a class for Swift as [LevelOption] is. */
@Immutable data class SortOption(val sort: PathSort)

/**
 * A choice of the Trail page's direction filter (segments/path-filters): Both ways when [direction] is null, else one way only that way.
 */
@Immutable data class WayChoice(val direction: SegmentDirection? = null)

/**
 * What the Trail page's chips offer (segments/path-filters), each list empty when its chip doesn't show: [sports] the sport groups after
 * All, [levels] Easiest to Extreme then null for Unrated, [ways] Both ways then one way by direction, [lengths] short to long, and
 * [searchable] whether the search field shows.
 */
@Immutable
data class PathFilterOptions(
    val sports: ImmutableList<String> = persistentListOf(),
    val levels: ImmutableList<LevelOption> = persistentListOf(),
    val ways: ImmutableList<WayChoice> = persistentListOf(),
    val lengths: ImmutableList<LengthOption> = persistentListOf(),
    val searchable: Boolean = false,
)

/**
 * The Trail page's filter and sort choices (segments/path-filters): [levels] (null for Unrated), [ways] and [lengths], none chosen meaning
 * every row, and [sort]. The search query is the page's text field, passed in.
 */
@Immutable
data class PathFilter(
    val levels: ImmutableSet<TrailRating?> = persistentSetOf(),
    val ways: ImmutableSet<WayChoice> = persistentSetOf(),
    val lengths: ImmutableSet<LengthBand> = persistentSetOf(),
    val sort: PathSort = PathSort.Longest,
) {
    /** Whether any filter is chosen (not the search or the sort). */
    val active: Boolean
        get() = levels.isNotEmpty() || ways.isNotEmpty() || lengths.isNotEmpty()

    fun toggled(level: TrailRating?): PathFilter = copy(levels = levels.toggled(level))

    fun toggled(way: WayChoice): PathFilter = copy(ways = ways.toggled(way))

    fun toggled(length: LengthBand): PathFilter = copy(lengths = lengths.toggled(length))

    /** Without every filter; the sort stays. */
    fun cleared(): PathFilter = PathFilter(sort = sort)

    /** Without the choices [options] no longer offers, as when the sport changes. */
    fun pruned(options: PathFilterOptions): PathFilter =
        copy(
            levels = levels.filter { level -> options.levels.any { it.rating == level } }.toImmutableSet(),
            ways = ways.filter { it in options.ways }.toImmutableSet(),
            lengths = lengths.filter { band -> options.lengths.any { it.band == band } }.toImmutableSet(),
        )

    /**
     * [content]'s rows that [query] and the chosen filters keep, in [sort] order, and its sections holding them: a section whose name
     * matches [query] keeps every row the filters keep, one left with a single row is listed as that row, and one left with none drops out.
     * [sport] is the page's sport group, or null for All. Nothing changes while nothing is chosen in the usual order.
     */
    fun apply(content: PathContent, query: String, sport: String?, units: UnitSystem): PathContent {
        val key = searchKey(query)
        val unchanged = key.isEmpty() && !active && sort == PathSort.Longest
        return if (unchanged) content else narrowed(content, key, sport, units)
    }

    /** [apply] once something is chosen or searched, [key] being the folded query. */
    private fun narrowed(content: PathContent, key: String, sport: String?, units: UnitSystem): PathContent {
        fun kept(row: SegmentGroupRow) = matchesFilters(row, sport, units)
        fun named(row: SegmentGroupRow) = key.isEmpty() || row.names().any { key in it }
        val order = comparator(sort)
        val groups = content.groups.filter { kept(it) && named(it) }.sortedWith(order)
        if (content.sectioned.isEmpty()) return content.copy(groups = groups.toImmutableList())
        val items =
            content.sectioned.mapNotNull { item ->
                when (item) {
                    is PathListItem.Group -> item.takeIf { kept(item.group) && named(item.group) }
                    is PathListItem.Section -> {
                        val sectionNamed = key.isNotEmpty() && key in searchKey(item.name)
                        narrowedSection(item, item.rows.filter { kept(it) && (sectionNamed || named(it)) }.sortedWith(order), sport)
                    }
                }
            }
        val sortedItems = items.sortedWith(compareBy(order) { it.first() })
        // A row a section's name brought in is listed too.
        val listed = sortedItems.flatMap { it.rows() }
        return content.copy(groups = listed.sortedWith(order).toImmutableList(), sectioned = sortedItems.toImmutableList())
    }

    /** [section] holding [rows]: dropped with none, that row with one, else the section of them. */
    private fun narrowedSection(section: PathListItem.Section, rows: List<SegmentGroupRow>, sport: String?): PathListItem? =
        when (rows.size) {
            0 -> null
            1 -> PathListItem.Group(rows.single())
            else -> PathListItem.Section(section.name, rows.toImmutableList(), SegmentGroupRow.groupOf(rows.flatMap { it.members }, sport))
        }

    private fun matchesFilters(row: SegmentGroupRow, sport: String?, units: UnitSystem): Boolean =
        (levels.isEmpty() || row.rating in levels) &&
            (lengths.isEmpty() || LengthBand.of(row.shown.distanceMeters, units) in lengths) &&
            (ways.isEmpty() || row.wayChoices(sport).any { it in ways })

    companion object {
        /** Every sort choice, in the order the page offers them. */
        val sorts: ImmutableList<SortOption> = PathSort.entries.map(::SortOption).toImmutableList()

        /** More rows than this, before any filter, and the page offers its search field and filters other than sport. */
        const val SEARCH_ABOVE_ROWS = 3

        /**
         * What the chips offer for [content] narrowed to [sport] (segments/path-filters): the path's sport groups when two or more; and,
         * with more than [SEARCH_ABOVE_ROWS] rows, the search, and the levels, ways and length bands when the rows differ in them.
         */
        fun options(content: PathContent, sport: String?, units: UnitSystem): PathFilterOptions {
            val order = SegmentSportGroup.all
            val sports =
                content.path
                    ?.sports
                    .orEmpty()
                    .distinct()
                    .sortedBy { order.indexOf(it).takeIf { i -> i >= 0 } ?: order.size }
                    .takeIf { it.size >= 2 }
                    .orEmpty()
            val rows = content.groups
            if (rows.size <= SEARCH_ABOVE_ROWS) return PathFilterOptions(sports = sports.toImmutableList())
            val rated = rows.mapNotNull { it.rating }.toSet()
            // Offered when the rows differ in level: two levels, or one beside unrated rows.
            val levels =
                (TrailRating.entries.filter { it in rated }.map(::LevelOption) +
                        listOfNotNull(LevelOption(null).takeIf { rows.any { row -> row.rating == null } }))
                    .takeIf { rated.isNotEmpty() && it.size >= 2 }
                    .orEmpty()
            // Offered when the rows differ in which ways they go: two choices, or one that some row doesn't go.
            val choices = rows.map { it.wayChoices(sport) }
            val ways =
                choices
                    .flatten()
                    .distinct()
                    .sortedBy { it.direction?.ordinal ?: -1 }
                    .takeIf { it.size >= 2 || (it.size == 1 && choices.any { row -> it.single() !in row }) }
                    .orEmpty()
            val lengths = rows.map { LengthBand.of(it.shown.distanceMeters, units) }.distinct().sorted().takeIf { it.size >= 2 }.orEmpty()
            return PathFilterOptions(
                sports.toImmutableList(),
                levels.toImmutableList(),
                ways.toImmutableList(),
                lengths.map(::LengthOption).toImmutableList(),
                searchable = true,
            )
        }

        /** [sort]'s order; ties, and rows with nothing to sort by, go longest first, then by name. */
        fun comparator(sort: PathSort): Comparator<SegmentGroupRow> {
            val longest = compareByDescending<SegmentGroupRow> { it.shown.distanceMeters }.thenBy { it.shown.currentName }
            val first: Comparator<SegmentGroupRow>? =
                when (sort) {
                    PathSort.Longest -> null
                    PathSort.Shortest -> compareBy { it.shown.distanceMeters }
                    PathSort.Name -> compareBy { searchKey(SegmentGroupRow.displayName(it.shown.currentName, it.bothWays)) }
                    PathSort.MostClimbing -> compareBy(nullsLast(reverseOrder())) { it.shown.elevationGain }
                    PathSort.EasiestFirst -> compareBy(nullsLast()) { it.rating?.ordinal }
                    PathSort.HardestFirst -> compareBy(nullsLast(reverseOrder())) { it.rating?.ordinal }
                }
            return first?.then(longest) ?: longest
        }
    }
}

/** The names a search looks in: the row's shown name and every member's current name, folded. */
private fun SegmentGroupRow.names(): List<String> =
    (listOf(SegmentGroupRow.displayName(shown.currentName, bothWays)) + members.map { it.currentName }).map(::searchKey)

/** The direction choices [this] row matches for [sport], or for any sport when null: Both ways, or one way only in a direction. */
internal fun SegmentGroupRow.wayChoices(sport: String?): List<WayChoice> =
    ways
        .filter { sport == null || it.sport == sport }
        .mapNotNull { way ->
            when {
                way.bothWays -> WayChoice()
                way.direction != null -> WayChoice(way.direction)
                else -> null
            }
        }
        .distinct()

private fun PathListItem.first(): SegmentGroupRow =
    when (this) {
        is PathListItem.Group -> group
        is PathListItem.Section -> rows.first()
    }

private fun PathListItem.rows(): List<SegmentGroupRow> =
    when (this) {
        is PathListItem.Group -> listOf(group)
        is PathListItem.Section -> rows
    }

private fun <T> ImmutableSet<T>.toggled(value: T): ImmutableSet<T> = (if (value in this) this - value else this + value).toImmutableSet()
